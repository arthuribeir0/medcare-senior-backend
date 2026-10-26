#!/usr/bin/env bash
# Instala e sobe o MedCare Sênior numa VM Ubuntu (Oracle Cloud Always Free, Ampere A1 ou AMD micro). Idempotente: pode
# rodar de novo a qualquer momento (para atualizar, renovar a configuração ou depois de um erro) sem duplicar nada.
#
# Uso, na VM, dentro da pasta do deploy copiada do repositório (~/medcare):
#   bash instalar-vm.sh --dominio SEU-SUBDOMINIO.duckdns.org --email voce@exemplo.com [opções]
#
# Opções:
#   --dominio D           domínio público (obrigatório; DuckDNS ou outro já apontando para o IP da VM)
#   --email E             e-mail para o Let's Encrypt avisar sobre o vencimento do certificado (obrigatório)
#   --gerar-segredos      preenche no .env.prod, SÓ se estiverem vazios, POSTGRES_USER, POSTGRES_PASSWORD e JWT_SECRET com
#                         valores aleatórios (sem isso, o script para e pede que você os preencha)
#   --ghcr-usuario U      se o pacote da imagem no ghcr.io for privado: faz docker login como U, pedindo o token
#                         (read:packages) sem eco. Com o pacote público, não precisa.
#   --swap                cria 2 GB de swap (recomendado na AMD micro de 1 GB)
#   --backup-diario       agenda o deploy/backup.sh todo dia às 02:30 (retenção de 14 dias)
#   --duckdns S           agenda a atualização do IP no DuckDNS para o subdomínio S, a cada 30 minutos; o token do
#                         DuckDNS é pedido sem eco e fica só em ~/.config/medcare/duckdns-token (permissão 600)
#   --sem-certificado     sobe só em HTTP inicial (para testar antes de o DNS propagar); rode de novo sem a opção depois
#
# Nunca imprime segredos. Não abre as portas 5432 nem 8080: só 22, 80 e 443.
set -euo pipefail

DOMINIO_ARG=""; EMAIL=""; GERAR=0; GHCR_USUARIO=""; SWAP=0; BACKUP=0; DUCKDNS=""; SEM_CERT=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --dominio) DOMINIO_ARG="$2"; shift 2 ;;
    --email) EMAIL="$2"; shift 2 ;;
    --gerar-segredos) GERAR=1; shift ;;
    --ghcr-usuario) GHCR_USUARIO="$2"; shift 2 ;;
    --swap) SWAP=1; shift ;;
    --backup-diario) BACKUP=1; shift ;;
    --duckdns) DUCKDNS="$2"; shift 2 ;;
    --sem-certificado) SEM_CERT=1; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "Opção desconhecida: $1 (veja --help)" >&2; exit 2 ;;
  esac
done
[[ -n "$DOMINIO_ARG" && -n "$EMAIL" ]] || { echo "Informe --dominio e --email (veja --help)" >&2; exit 2; }

cd "$(dirname "$0")"
PASTA="$(pwd)"
[[ -f docker-compose.prod.yml && -f .env.prod.example ]] || { echo "Rode dentro da pasta do deploy (com docker-compose.prod.yml)" >&2; exit 1; }
passo() { echo; echo "==> $*"; }
compose() { sudo docker compose -f docker-compose.prod.yml --env-file .env.prod "$@"; }

# Lê e grava variáveis do .env.prod sem imprimir valores
valor() { sed -n "s/^$1=//p" .env.prod | tail -n 1; }
definir() {
  if grep -q "^$1=" .env.prod; then
    local tmp; tmp="$(mktemp)"
    awk -v chave="$1" -v novo="$2" 'BEGIN { FS = OFS = "=" } $1 == chave { print chave "=" novo; next } { print }' .env.prod > "$tmp"
    cat "$tmp" > .env.prod; rm -f "$tmp"
  else
    echo "$1=$2" >> .env.prod
  fi
}

passo "Pacotes do sistema e firewall persistente"
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq ca-certificates curl openssl iptables-persistent netfilter-persistent > /dev/null

passo "Docker Engine e plugin compose"
if ! command -v docker > /dev/null; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo docker compose version > /dev/null || { echo "Plugin docker compose ausente" >&2; exit 1; }
sudo usermod -aG docker "$USER" || true

passo "Portas 80 e 443 no iptables do Ubuntu da Oracle (antes da regra que rejeita o resto)"
for porta in 80 443; do
  if ! sudo iptables -C INPUT -p tcp -m state --state NEW --dport "$porta" -j ACCEPT 2> /dev/null; then
    # Insere antes do primeiro REJECT (a imagem da Oracle termina a cadeia INPUT com REJECT); sem REJECT, no fim
    linha="$(sudo iptables -L INPUT --line-numbers -n | awk '$2 == "REJECT" { print $1; exit }')"
    if [[ -n "$linha" ]]; then
      sudo iptables -I INPUT "$linha" -p tcp -m state --state NEW --dport "$porta" -j ACCEPT
    else
      sudo iptables -A INPUT -p tcp -m state --state NEW --dport "$porta" -j ACCEPT
    fi
  fi
done
sudo netfilter-persistent save > /dev/null

if [[ "$SWAP" == 1 ]] && ! swapon --show | grep -q /swapfile; then
  passo "Swap de 2 GB"
  [[ -f /swapfile ]] || sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile > /dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab > /dev/null
fi

passo "Arquivo .env.prod"
if [[ ! -f .env.prod ]]; then
  cp .env.prod.example .env.prod
fi
chmod 600 .env.prod
[[ -n "$(valor DOMINIO)" ]] || definir DOMINIO "$DOMINIO_ARG"
if [[ "$GERAR" == 1 ]]; then
  [[ -n "$(valor POSTGRES_USER)" ]] || definir POSTGRES_USER medcare
  [[ -n "$(valor POSTGRES_PASSWORD)" ]] || definir POSTGRES_PASSWORD "$(openssl rand -hex 24)"
  [[ -n "$(valor JWT_SECRET)" ]] || definir JWT_SECRET "$(openssl rand -base64 48 | tr -d '\n=')"
fi
faltando=()
for var in DOMINIO POSTGRES_USER POSTGRES_PASSWORD POSTGRES_DB JWT_SECRET; do
  [[ -n "$(valor "$var")" ]] || faltando+=("$var")
done
if [[ ${#faltando[@]} -gt 0 ]]; then
  echo "Preencha no $PASTA/.env.prod: ${faltando[*]} (ou rode de novo com --gerar-segredos)." >&2
  exit 1
fi
if [[ "$(valor DOMINIO)" != "$DOMINIO_ARG" ]]; then
  echo "O .env.prod tem outro DOMINIO; corrija o arquivo ou o --dominio." >&2
  exit 1
fi

mkdir -p segredos certbot/www certbot/conf
chmod 700 segredos
if [[ "$(valor FCM_ENABLED)" == "true" && ! -f segredos/firebase-adminsdk.json ]]; then
  echo "FCM_ENABLED=true, mas falta $PASTA/segredos/firebase-adminsdk.json (copie do seu PC com scp; ver docs/implantacao.md)." >&2
  exit 1
fi

passo "Imagem do backend (ghcr.io)"
if [[ -n "$GHCR_USUARIO" ]]; then
  read -r -s -p "Token do GitHub com read:packages para $GHCR_USUARIO (não aparece na tela): " TOKEN_GHCR; echo
  printf '%s' "$TOKEN_GHCR" | sudo docker login ghcr.io -u "$GHCR_USUARIO" --password-stdin > /dev/null
  unset TOKEN_GHCR
fi
if ! compose pull --quiet; then
  echo "Não consegui baixar a imagem. Se o pacote for privado, rode de novo com --ghcr-usuario SEU_USUARIO_GITHUB," >&2
  echo "ou torne o pacote público (docs/implantacao.md, seção da imagem)." >&2
  exit 1
fi

CERT="certbot/conf/live/$DOMINIO_ARG/fullchain.pem"
if [[ -f "$CERT" ]]; then
  passo "Certificado já existe: subindo em HTTPS"
  definir NGINX_MODO https
  compose up -d --remove-orphans
  compose up -d --force-recreate nginx
else
  # Ovo e galinha: o nginx em HTTPS não sobe sem o certificado, e o certificado precisa do nginx servindo o desafio.
  # Então sobe primeiro só em HTTP (variante http-inicial), emite o certificado pelo webroot e só então troca.
  passo "Primeira subida só em HTTP (para emitir o certificado)"
  definir NGINX_MODO http-inicial
  compose up -d --remove-orphans
  compose up -d --force-recreate nginx
  for _ in $(seq 1 30); do
    curl -fsS -o /dev/null "http://127.0.0.1/nginx-saude" && break
    sleep 2
  done
  if [[ "$SEM_CERT" == 1 ]]; then
    echo "Subiu só em HTTP (--sem-certificado). Quando o DNS apontar para esta VM, rode o script de novo sem a opção."
    exit 0
  fi
  passo "Certificado Let's Encrypt para $DOMINIO_ARG"
  compose --profile certbot run --rm certbot certonly --webroot -w /var/www/certbot -d "$DOMINIO_ARG" \
    --email "$EMAIL" --agree-tos --no-eff-email --non-interactive
  definir NGINX_MODO https
  compose up -d --force-recreate nginx
fi

passo "Tarefas agendadas (cron do usuário $USER)"
agendar() {
  local marca="$1" linha="$2"
  ( crontab -l 2> /dev/null | grep -v "# $marca\$" || true; echo "$linha # $marca" ) | crontab -
}
agendar medcare-renovacao "17 3 * * * cd $PASTA && sudo docker compose -f docker-compose.prod.yml --env-file .env.prod --profile certbot run --rm certbot renew --quiet && sudo docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T nginx nginx -s reload"
if [[ "$BACKUP" == 1 ]]; then
  chmod +x backup.sh
  agendar medcare-backup "30 2 * * * BACKUP_DIR=\$HOME/medcare-backups $PASTA/backup.sh >> \$HOME/medcare-backups.log 2>&1"
fi
if [[ -n "$DUCKDNS" ]]; then
  mkdir -p "$HOME/.config/medcare" && chmod 700 "$HOME/.config/medcare"
  if [[ ! -s "$HOME/.config/medcare/duckdns-token" ]]; then
    read -r -s -p "Token do DuckDNS (não aparece na tela): " TOKEN_DUCK; echo
    ( umask 077; printf '%s' "$TOKEN_DUCK" > "$HOME/.config/medcare/duckdns-token" )
    unset TOKEN_DUCK
  fi
  agendar medcare-duckdns "*/30 * * * * curl -fsS \"https://www.duckdns.org/update?domains=$DUCKDNS&token=\$(cat \$HOME/.config/medcare/duckdns-token)&ip=\" > /dev/null"
fi
# O backup.sh precisa do docker sem sudo: vale a partir do próximo login (grupo docker)

passo "Conferência"
compose ps
sleep 5
if curl -fsS "https://$DOMINIO_ARG/actuator/health"; then
  echo; echo "Pronto: https://$DOMINIO_ARG está no ar. Confira tudo do seu PC com deploy/verificar-producao.ps1."
else
  echo "A saúde por HTTPS ainda não respondeu. Veja: sudo docker compose -f docker-compose.prod.yml --env-file .env.prod logs --tail 100" >&2
  exit 1
fi
