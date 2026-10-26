# Implantação do backend (Oracle Cloud Always Free + Docker Compose + HTTPS)

Roteiro para colocar a API no ar numa VM gratuita, com HTTPS e domínio gratuito. Nada aqui tem senha, token ou chave:
esses valores são gerados e guardados **só na VM** (arquivo `deploy/.env.prod` e pasta `deploy/segredos/`).

Capacidade estimada e plano B local: [capacidade.md](capacidade.md).

## Visão geral

```
Celular (app) ──HTTPS──> nginx :443 ──> backend :8080 ──> PostgreSQL :5432
                         (rede "borda")   (redes "borda" e "banco")   (rede "banco", interna, sem porta no host)
```

- `deploy/docker-compose.prod.yml`: PostgreSQL 16, backend (imagem do GitHub Container Registry, publicada pela
  integração contínua no `main`) e nginx (proxy reverso, HTTPS com Let's Encrypt).
- O banco não publica porta: só o backend o enxerga. Só o nginx publica as portas 80 e 443.
- As migrations do Flyway (V1 até a última) rodam sozinhas quando o backend sobe.
- A imagem é multiarquitetura (linux/amd64 e linux/arm64): serve tanto para VM Ampere (ARM) quanto AMD.

## 1. Conta e VM na Oracle Cloud (Always Free)

1. Crie a conta em <https://www.oracle.com/cloud/free/> (pede cartão só para verificação; o nível Always Free não é
   cobrado). Escolha a região mais próxima (ex.: Brazil East – São Paulo). A região não muda depois.
2. Gere uma chave SSH no seu PC (PowerShell):
   ```powershell
   ssh-keygen -t ed25519 -f $HOME\.ssh\medcare_oci -C "medcare-oci"
   ```
   A chave privada (`medcare_oci`) nunca sai do seu PC. A pública (`medcare_oci.pub`) vai para a VM.
3. Menu **Compute → Instances → Create instance**:
   - **Image**: Canonical Ubuntu 24.04 (ou 22.04).
   - **Shape** (escolha uma das duas, ambas Always Free):
     - **Ampere A1 (VM.Standard.A1.Flex, ARM / linux/arm64)** — recomendada: 1 OCPU e 6 GB já sobram para o MedCare
       (o limite grátis é 4 OCPU e 24 GB no total da conta). Se aparecer "Out of capacity", tente outro domínio de
       disponibilidade ou mais tarde.
     - **AMD micro (VM.Standard.E2.1.Micro, linux/amd64)** — 1 GB de memória: funciona com ajuste de memória (passo 4
       e `BACKEND_MEM_LIMIT=640m`), mas com pouca folga.
   - **Networking**: VCN nova com sub-rede pública e **Assign a public IPv4 address**.
   - **Add SSH keys**: cole o conteúdo de `medcare_oci.pub`.
4. Anote o **IP público** da instância.

## 2. Portas: firewall da Oracle e do sistema

1. **Na Oracle** (Networking → Virtual Cloud Networks → sua VCN → Security Lists → Default): adicione regras de
   entrada (Ingress) TCP para `0.0.0.0/0` nas portas **80** e **443**. A porta 22 (SSH) já vem liberada; se puder,
   restrinja-a ao seu IP.
2. **No Ubuntu da Oracle** o iptables bloqueia tudo menos o SSH. Entre na VM e libere 80 e 443:
   ```powershell
   ssh -i $HOME\.ssh\medcare_oci ubuntu@IP_PUBLICO
   ```
   ```bash
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
   sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
   sudo netfilter-persistent save
   ```

## 3. Docker e Docker Compose na VM

```bash
sudo apt-get update && sudo apt-get -y upgrade
curl -fsSL https://get.docker.com | sudo sh        # Docker Engine + plugin compose (amd64 e arm64)
sudo usermod -aG docker ubuntu && newgrp docker
docker version && docker compose version
```

## 4. Só na VM AMD de 1 GB: memória de troca (swap)

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

No `.env.prod`, mantenha `BACKEND_MEM_LIMIT=640m` (a JVM usa 75% disso de heap). Na Ampere A1 pode subir para `2g`.

## 5. Domínio gratuito (DuckDNS)

1. Entre em <https://www.duckdns.org> (login com GitHub ou Google), crie um subdomínio (ex.: `medcare-senior`) e
   aponte para o IP público da VM. O endereço fica `medcare-senior.duckdns.org`.
2. O IP público da Oracle é fixo enquanto a instância existir; se trocar, atualize no site do DuckDNS.
   (Opcional) Atualização automática na VM, com o token do DuckDNS guardado só na VM:
   ```bash
   mkdir -p ~/duckdns && chmod 700 ~/duckdns
   nano ~/duckdns/token              # cole o token do DuckDNS e salve
   chmod 600 ~/duckdns/token
   (crontab -l 2>/dev/null; echo '*/30 * * * * curl -fsS "https://www.duckdns.org/update?domains=SEU_SUBDOMINIO&token=$(cat ~/duckdns/token)&ip=" >/dev/null') | crontab -
   ```
3. Confira: `nslookup SEU_SUBDOMINIO.duckdns.org` deve devolver o IP da VM.

## 6. Arquivos do deploy na VM

Do seu PC, copie a pasta `deploy/` do repositório (só ela vai para a VM):

```powershell
scp -i $HOME\.ssh\medcare_oci -r C:\Users\arthu\Projetos\medcare-senior-backend\deploy ubuntu@IP_PUBLICO:~/medcare
```

Na VM:

```bash
cd ~/medcare
cp .env.prod.example .env.prod && chmod 600 .env.prod
nano .env.prod
```

Preencha: `DOMINIO`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (gere com `openssl rand -base64 32`), `JWT_SECRET` (gere
com `openssl rand -base64 48`), `IMAGE_TAG` e `NGINX_MODO=http-inicial`. Os valores ficam só nesse arquivo.

Credencial do Firebase (só se for ligar o push real):

```powershell
# No PC: copia o JSON da conta de serviço para a VM, sem passar por repositório
scp -i $HOME\.ssh\medcare_oci C:\Users\arthu\segredos\SEU_ARQUIVO-adminsdk.json ubuntu@IP_PUBLICO:~/medcare/segredos/firebase-adminsdk.json
```

```bash
mkdir -p ~/medcare/segredos && chmod 700 ~/medcare/segredos
chmod 644 ~/medcare/segredos/firebase-adminsdk.json   # o contêiner roda como usuário sem privilégios e só lê
```

Sem o arquivo, deixe `FCM_ENABLED=false` (o backend sobe, mas avisa no log que os avisos não serão entregues).

## 7. Imagem do GitHub Container Registry

A integração contínua publica `ghcr.io/arthuribeir0/medcare-senior-backend` a cada push no `main` (tags `latest` e o
SHA curto do commit). Se o pacote estiver privado, na VM:

```bash
# Token do GitHub com permissão read:packages (crie em Settings > Developer settings > Personal access tokens)
docker login ghcr.io -u arthuribeir0      # cole o token quando pedir a senha; ele fica só na VM
```

(Alternativa: no GitHub, Packages → medcare-senior-backend → Package settings → Change visibility → Public.)

## 8. Primeira subida (só HTTP, para emitir o certificado)

```bash
cd ~/medcare
docker compose -f docker-compose.prod.yml --env-file .env.prod pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d
docker compose -f docker-compose.prod.yml --env-file .env.prod ps          # postgres e backend "healthy"
docker compose -f docker-compose.prod.yml --env-file .env.prod logs backend | grep -E "Flyway|Successfully applied|Started"
curl -s http://SEU_SUBDOMINIO.duckdns.org/nginx-saude                       # "ok"
```

As migrations rodam na subida do backend (o log mostra `Successfully applied N migrations` na primeira vez).

### Restaurar dados de outro banco (opcional)

No PC (banco de desenvolvimento) e depois na VM, sem expor a senha na linha de comando:

```powershell
# PC: gera o dump no formato do pg_restore (o usuário e o banco são os do seu .env de desenvolvimento)
docker exec medcare_postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > medcare.dump
scp -i $HOME\.ssh\medcare_oci medcare.dump ubuntu@IP_PUBLICO:~/medcare/
```

```bash
# VM: para o backend, restaura e sobe de novo (as migrations já aplicadas são reconhecidas pelo Flyway)
docker compose -f docker-compose.prod.yml --env-file .env.prod stop backend
docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' < medcare.dump
docker compose -f docker-compose.prod.yml --env-file .env.prod start backend
rm medcare.dump
```

## 9. Certificado HTTPS (Let's Encrypt) e troca para HTTPS

```bash
cd ~/medcare
docker compose -f docker-compose.prod.yml --env-file .env.prod --profile certbot run --rm certbot \
  certonly --webroot -w /var/www/certbot -d SEU_SUBDOMINIO.duckdns.org \
  --email SEU_EMAIL --agree-tos --no-eff-email
# No .env.prod: NGINX_MODO=https
nano .env.prod
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --force-recreate nginx
curl -s https://SEU_SUBDOMINIO.duckdns.org/actuator/health                 # {"status":"UP"}
curl -sI http://SEU_SUBDOMINIO.duckdns.org/api/pacientes | head -n 1           # 301 para https
```

Renovação automática (o certificado vale 90 dias):

```bash
(crontab -l 2>/dev/null; echo '0 3 * * 1 cd ~/medcare && docker compose -f docker-compose.prod.yml --env-file .env.prod --profile certbot run --rm certbot renew --quiet && docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload') | crontab -
```

## 10. Conferir os jobs e os logs

```bash
cd ~/medcare
docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f --since 10m backend
docker compose -f docker-compose.prod.yml --env-file .env.prod logs backend | grep -E "Verificação de doses|Retentativa de avisos|Reabastecimento de doses"
```

- `Verificação de doses: ...` a cada minuto (doses encerradas e avisos criados/enviados).
- `Retentativa de avisos: ...` a cada 5 minutos.
- `Reabastecimento de doses: ...` todo dia às 00:05.
- Com `FCM_ENABLED=false` aparece, na subida, o aviso de que os avisos não serão entregues, e cada aviso fica
  registrado no banco como falha com motivo `PUSH_DESLIGADO` (`evento_notificacao.motivo_falha`), sem retentativa.

Verificação de ponta a ponta contra a URL pública: `deploy/verificar-producao.ps1` (ver o próprio script).

## 11. Atualizar a versão do backend

```bash
cd ~/medcare
nano .env.prod                         # IMAGE_TAG=<SHA curto do commit do main> (ou latest)
docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

Para voltar uma versão, use o SHA anterior em `IMAGE_TAG` e repita.

## 12. Cópia de segurança do banco

```bash
mkdir -p ~/backups && chmod 700 ~/backups
(crontab -l 2>/dev/null; echo '30 2 * * * cd ~/medcare && docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T postgres sh -c '"'"'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc'"'"' > ~/backups/medcare-$(date +\%F).dump && find ~/backups -name "*.dump" -mtime +14 -delete') | crontab -
```

## 13. App apontando para a produção

No build do app (EAS, perfis `preview` ou `production`), defina `EXPO_PUBLIC_API_URL=https://SEU_SUBDOMINIO.duckdns.org`.
O app de release recusa endereço sem `https://` e não usa tráfego em texto claro.

## 14. Teste de carga: isentar o IP do gerador de carga do limite do nginx

O nginx limita cada IP a 20 requisições por segundo na API (rajada de 80), o que derrubaria um teste de carga feito
de uma só máquina (JMeter). A isenção existe, mas vem **desligada**: `deploy/nginx/carga-isenta.conf` não tem nenhum
IP ativo, e todos os clientes seguem o limite.

```bash
cd ~/medcare
nano nginx/carga-isenta.conf           # descomente e troque o IP de exemplo pelo IP público da máquina do JMeter
docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -t
docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload
# ... teste de carga ...
nano nginx/carga-isenta.conf           # comente a linha de novo
docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload
```

Como funciona: um `geo` marca os IPs do arquivo e um `map` dá a eles a chave vazia; o `limit_req` não conta
requisições com chave vazia. Os demais IPs continuam com o limite de produção. Nunca deixe um IP isento depois do
teste.

## Plano B: backend no PC com túnel HTTPS

Se a VM não sair (falta de capacidade, cartão recusado), o backend roda no PC e um túnel dá a URL `https://` pública.
Passo a passo em [capacidade.md](capacidade.md#plano-b-backend-local-com-túnel-https).
