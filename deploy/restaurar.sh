#!/usr/bin/env bash
# Restaura um backup feito pelo backup.sh, SUBSTITUINDO os dados atuais do banco. Roda na VM, na pasta do deploy.
#
#   ./restaurar.sh ~/medcare-backups/medcare-20261026-023000.dump
#   ./restaurar.sh --sim ARQUIVO        # sem a pergunta de confirmação (para scripts)
#
# Para o backend durante a restauração (ninguém grava no meio) e o sobe de novo no fim; o Flyway reconhece as
# migrations que vieram no backup. BACKUP_CONTAINER, como no backup.sh, troca o compose por um contêiner avulso (teste
# local; aí o backend não é parado).
set -euo pipefail

cd "$(dirname "$0")"
confirmar=1
if [[ "${1:-}" == "--sim" ]]; then
  confirmar=0
  shift
fi
arquivo="${1:-}"
if [[ -z "$arquivo" || ! -f "$arquivo" ]]; then
  echo "Uso: ./restaurar.sh [--sim] ARQUIVO.dump" >&2
  exit 2
fi

compose() { docker compose -f docker-compose.prod.yml --env-file .env.prod "$@"; }
no_banco() {
  if [[ -n "${BACKUP_CONTAINER:-}" ]]; then
    docker exec -i "$BACKUP_CONTAINER" "$@"
  else
    compose exec -T postgres "$@"
  fi
}

# Confere o arquivo antes de tocar no banco
no_banco pg_restore --list > /dev/null < "$arquivo" || { echo "ERRO: $arquivo não é um backup legível" >&2; exit 1; }

if [[ "$confirmar" == 1 ]]; then
  read -r -p "Isto APAGA os dados atuais e restaura $arquivo. Digite RESTAURAR para continuar: " resposta
  [[ "$resposta" == "RESTAURAR" ]] || { echo "Cancelado."; exit 1; }
fi

backend_parado=0
if [[ -z "${BACKUP_CONTAINER:-}" ]]; then
  compose stop backend
  backend_parado=1
fi
# Sobe o backend de novo mesmo se a restauração falhar no meio
trap 'if [[ "$backend_parado" == 1 ]]; then compose start backend; fi' EXIT

# --single-transaction: ou restaura tudo, ou nada muda
no_banco sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --single-transaction' \
  < "$arquivo"
echo "Restauração concluída: $arquivo"
