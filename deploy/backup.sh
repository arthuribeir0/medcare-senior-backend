#!/usr/bin/env bash
# Cópia de segurança do banco de produção: pg_dump no formato custom (comprimido), com retenção configurável.
# Roda na VM, na pasta do deploy (~/medcare), à mão ou pelo cron (o instalar-vm.sh agenda com --backup-diario).
#
#   ./backup.sh                          # grava em ~/medcare-backups e apaga os de mais de 14 dias
#   BACKUP_DIR=/mnt/volume BACKUP_RETENCAO_DIAS=30 ./backup.sh
#
# Variáveis (opcionais):
#   BACKUP_DIR            pasta dos arquivos (padrão ~/medcare-backups; FORA do repositório e do deploy)
#   BACKUP_RETENCAO_DIAS  apaga cópias com mais de N dias (padrão 14; 0 = nunca apaga)
#   BACKUP_CONTAINER      nome de um contêiner do PostgreSQL para usar no lugar do serviço "postgres" do compose
#                         (usado no teste local com um banco descartável)
#
# A senha não aparece em lugar nenhum: o pg_dump roda dentro do contêiner do banco, com as variáveis dele.
# O arquivo gerado tem dados pessoais e de saúde: fica com permissão 600 e nunca vai para o git.
set -euo pipefail

cd "$(dirname "$0")"
BACKUP_DIR="${BACKUP_DIR:-$HOME/medcare-backups}"
BACKUP_RETENCAO_DIAS="${BACKUP_RETENCAO_DIAS:-14}"

# Executa um comando no contêiner do banco, com a entrada padrão ligada
no_banco() {
  if [[ -n "${BACKUP_CONTAINER:-}" ]]; then
    docker exec -i "$BACKUP_CONTAINER" "$@"
  else
    docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres "$@"
  fi
}

umask 077
mkdir -p "$BACKUP_DIR"
arquivo="$BACKUP_DIR/medcare-$(date +%Y%m%d-%H%M%S).dump"
parcial="$arquivo.parcial"
trap 'rm -f "$parcial"' EXIT

no_banco sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --compress=6 --no-owner' > "$parcial"

# Confere que o arquivo é um dump legível antes de considerá-lo válido
if [[ ! -s "$parcial" ]] || ! no_banco pg_restore --list > /dev/null < "$parcial"; then
  echo "ERRO: o dump saiu vazio ou ilegível; nada foi gravado" >&2
  exit 1
fi
mv "$parcial" "$arquivo"
chmod 600 "$arquivo"
echo "Backup gravado: $arquivo ($(du -h "$arquivo" | cut -f1))"

if [[ "$BACKUP_RETENCAO_DIAS" -gt 0 ]]; then
  find "$BACKUP_DIR" -maxdepth 1 -name 'medcare-*.dump' -type f -mtime +"$BACKUP_RETENCAO_DIAS" -print -delete \
    | sed 's/^/Removido (retenção): /'
fi
