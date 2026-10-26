# Implantação do backend (Oracle Cloud Always Free + Docker Compose + HTTPS)

Roteiro para colocar a API no ar numa VM gratuita, com HTTPS e domínio gratuito, escrito para quem nunca fez isso.
Nada aqui tem senha, token ou chave: esses valores são gerados e guardados **só na VM** (arquivo `deploy/.env.prod` e
pasta `deploy/segredos/`).

Capacidade estimada e plano B local: [capacidade.md](capacidade.md). Teste de carga contra o servidor:
[carga/README.md](carga/README.md).

## Visão geral

```
Celular (app) ──HTTPS──> nginx :443 ──> backend :8080 ──> PostgreSQL :5432
                         (rede "borda")   (redes "borda" e "banco")   (rede "banco", interna, sem porta no host)
```

- `deploy/docker-compose.prod.yml`: PostgreSQL 16, backend (imagem do GitHub Container Registry, publicada pela
  integração contínua no `main`) e nginx (proxy reverso, HTTPS com Let's Encrypt).
- O banco não publica porta: só o backend o enxerga. Só o nginx publica as portas 80 e 443. As portas **5432 e 8080
  nunca** são abertas para fora.
- As migrations do Flyway (V1 até a última) rodam sozinhas quando o backend sobe.
- A imagem é **multiarquitetura** (linux/amd64 e linux/arm64). O Docker baixa sozinho a variante certa para a VM,
  seja Ampere (ARM) ou AMD: não há nada a escolher na hora de baixar.

Ordem dos passos: VM (1) → rede e portas (2) → domínio (3) → arquivos (4) → `instalar-vm.sh` (5) → conferir (6).

## 1. Criar a VM na Oracle Cloud (Always Free)

1. Crie a conta em <https://www.oracle.com/cloud/free/>. O cartão é pedido só para verificação; o nível Always Free
   não é cobrado. Escolha a **Home Region** mais próxima (ex.: *Brazil East (Sao Paulo)*). Ela não muda depois, e os
   recursos grátis só existem na região de origem.
2. Gere uma chave SSH no seu PC (PowerShell):
   ```powershell
   ssh-keygen -t ed25519 -f $HOME\.ssh\medcare_oci -C "medcare-oci"
   ```
   A chave privada (`medcare_oci`) **nunca sai do seu PC**. A pública (`medcare_oci.pub`) vai para a VM.
3. No console da Oracle, abra o menu **☰ (Navigation menu) → Compute → Instances** e clique em **Create instance**.
   - **Name**: `medcare`.
   - **Placement**: deixe o *Availability domain* sugerido (se der "Out of capacity", volte aqui e troque).
   - **Image and shape → Edit**:
     - **Change image** → *Canonical Ubuntu* → **24.04** (ou 22.04) → *Select image*.
     - **Change shape**. Escolha uma das duas, ambas com o selo *Always Free-eligible*:

       | Shape | Arquitetura | Quando escolher |
       |---|---|---|
       | **Ampere** → `VM.Standard.A1.Flex` | ARM (linux/arm64) | **Recomendada.** Configure 1 OCPU e 6 GB (o grátis vai até 4 OCPU e 24 GB no total da conta). Sobra folga para o MedCare. |
       | **Specialty and previous generation** → `VM.Standard.E2.1.Micro` | AMD (linux/amd64) | Só se a Ampere estiver sem capacidade. 1 GB de memória: funciona com swap (opção `--swap` do script) e `BACKEND_MEM_LIMIT=640m`, com pouca folga. |

   - **Primary VNIC information**: *Create new virtual cloud network* e *Create new public subnet*, com **Automatically
     assign public IPv4 address** ligado.
   - **Add SSH keys** → *Upload public key files (.pub)* → escolha `medcare_oci.pub`.
   - **Boot volume**: deixe o padrão (até 200 GB no total são grátis).
   - Clique em **Create**. Quando o estado ficar **Running**, anote o **Public IP address** na página da instância.
4. Teste o acesso:
   ```powershell
   ssh -i $HOME\.ssh\medcare_oci ubuntu@IP_PUBLICO
   ```

## 2. Rede: regras de entrada da Oracle

Duas camadas bloqueiam portas: a lista de segurança da Oracle (fora da VM) e o iptables do Ubuntu (dentro dela). O
`instalar-vm.sh` cuida da segunda. A primeira é feita no console:

1. Na página da instância, em **Primary VNIC**, clique no nome da **Subnet** e depois, em **Security** (ou **Security
   Lists**), na **Default Security List for vcn-...**.
2. Aba **Security rules** (ou **Ingress Rules**) → **Add Ingress Rules**. Crie **duas** regras iguais, uma para cada
   porta:
   - **Source Type**: CIDR; **Source CIDR**: `0.0.0.0/0`; **IP Protocol**: TCP;
   - **Destination Port Range**: `80` (e, na outra regra, `443`);
   - **Description**: `HTTP` / `HTTPS`.
3. A regra da porta **22** (SSH) já vem criada. Se possível, edite-a e troque `0.0.0.0/0` pelo seu IP (`SEU_IP/32`).
4. **Não** crie regra para 5432 nem 8080.

> Por que o iptables também? A imagem Ubuntu da Oracle vem com o iptables rejeitando tudo menos o SSH, mesmo com a
> lista de segurança liberada. O script insere as regras de 80 e 443 **antes** da regra REJECT e as salva com
> `netfilter-persistent`, para valerem depois de reiniciar. Para conferir: `sudo iptables -L INPUT -n --line-numbers`.

## 3. Domínio gratuito (DuckDNS)

1. Entre em <https://www.duckdns.org> (login com GitHub ou Google).
2. Em **sub domain**, digite o nome (ex.: `medcare-senior`) e clique em **add domain**. O endereço fica
   `medcare-senior.duckdns.org`.
3. No campo **current ip** do domínio criado, coloque o IP público da VM e clique em **update ip**.
4. Confira no PC: `nslookup medcare-senior.duckdns.org` deve devolver o IP da VM. A propagação leva até alguns
   minutos.
5. O IP público da Oracle é fixo enquanto a instância existir. Mesmo assim, o script pode manter o DuckDNS
   atualizado a cada 30 minutos (opção `--duckdns`; o token, que aparece no topo da página do DuckDNS, é pedido sem
   eco e fica só na VM).

## 4. Arquivos do deploy na VM

Do seu PC, copie a pasta `deploy/` do repositório (só ela vai para a VM):

```powershell
scp -i $HOME\.ssh\medcare_oci -r C:\Users\arthu\Projetos\medcare-senior-backend\deploy ubuntu@IP_PUBLICO:~/medcare
```

Credencial do Firebase (só para o push real; sem ela, deixe `FCM_ENABLED=false`):

```powershell
ssh -i $HOME\.ssh\medcare_oci ubuntu@IP_PUBLICO "mkdir -p ~/medcare/segredos && chmod 700 ~/medcare/segredos"
scp -i $HOME\.ssh\medcare_oci C:\Users\arthu\segredos\SEU_ARQUIVO-adminsdk.json ubuntu@IP_PUBLICO:~/medcare/segredos/firebase-adminsdk.json
ssh -i $HOME\.ssh\medcare_oci ubuntu@IP_PUBLICO "chmod 644 ~/medcare/segredos/firebase-adminsdk.json"
```

(O contêiner roda com um usuário sem privilégios e só lê o arquivo; a pasta fica protegida com 700.) Depois, no
`.env.prod` da VM, use `FCM_ENABLED=true`.

## 5. Instalar e subir com `instalar-vm.sh`

### 5.1 A imagem no GitHub Container Registry

A integração contínua publica `ghcr.io/arthuribeir0/medcare-senior-backend` a cada push no `main` (tags `latest` e
SHA curto do commit). Escolha **uma** das opções:

- **Pacote público** (mais simples, recomendado; a imagem não contém segredos): no GitHub, seu perfil → **Packages**
  → `medcare-senior-backend` → **Package settings** → **Danger Zone** → **Change visibility** → **Public**.
- **Pacote privado**: crie um token em GitHub → **Settings** → **Developer settings** → **Personal access tokens** →
  **Tokens (classic)** → **Generate new token**, só com o escopo `read:packages`. Rode o script com
  `--ghcr-usuario arthuribeir0`, e ele pede o token sem eco (o `docker login` guarda a credencial só na VM).

### 5.2 Rodar

Na VM:

```bash
cd ~/medcare
bash instalar-vm.sh --dominio medcare-senior.duckdns.org --email SEU_EMAIL --gerar-segredos --backup-diario
# acrescente --swap na AMD micro, --duckdns medcare-senior para atualizar o IP e --ghcr-usuario se o pacote for privado
```

O que o script faz, nesta ordem (pode rodar de novo a qualquer momento: ele não duplica nada):

1. Instala Docker, o plugin compose e o `iptables-persistent`, e libera 80 e 443 no iptables de forma persistente.
2. Cria o `.env.prod` a partir do exemplo (permissão 600) e preenche o `DOMINIO`. Com `--gerar-segredos`, preenche
   **só se estiverem vazios** `POSTGRES_USER`, `POSTGRES_PASSWORD` e `JWT_SECRET` com valores aleatórios (o
   `openssl` gera direto no arquivo; nada aparece na tela). Sem a opção, ele para e lista o que falta preencher.
3. Baixa a imagem do backend.
4. **Certificado sem o problema do ovo e da galinha:** o nginx em HTTPS não sobe sem certificado, e o certificado
   precisa do nginx servindo o desafio do Let's Encrypt. Por isso o script sobe primeiro a variante `http-inicial`
   (só HTTP, só o desafio, nenhuma API em texto claro), emite o certificado pelo webroot e só então troca para
   `https`. Com o certificado já emitido, vai direto para HTTPS.
5. Agenda no cron: renovação do certificado todo dia às 03:17 (o certbot só renova perto do vencimento) com reload do
   nginx; com `--backup-diario`, o backup às 02:30; com `--duckdns`, a atualização do IP.
6. Confere `https://DOMINIO/actuator/health`.

Para ajustar outras variáveis (memória, pool, jobs, Swagger), edite `~/medcare/.env.prod` e rode
`sudo docker compose -f docker-compose.prod.yml --env-file .env.prod up -d`.

### 5.3 Variáveis do perfil `prod` e o que acontece se faltar

| Variável | Obrigatória | Se faltar ou estiver errada |
|---|---|---|
| `DOMINIO` | sim | O compose recusa subir ("defina DOMINIO no .env.prod"). |
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | sim | O compose recusa subir. Senha padrão de desenvolvimento: o backend recusa subir (`ValidacaoDeProducao`). |
| `JWT_SECRET` | sim | Ausente: o compose recusa. Com menos de 32 caracteres ou igual ao padrão de desenvolvimento: o backend recusa subir, e a mensagem cita só o nome da variável. |
| `FCM_ENABLED` | não (padrão `false`) | `false`: sobe, mas registra ERRO na subida, e os avisos de dose não chegam (ficam como falha `PUSH_DESLIGADO`). `true` sem `segredos/firebase-adminsdk.json`: o backend não sobe, e o script avisa antes. |
| `SWAGGER_ENABLED` | não (padrão `false`) | `false`: `/swagger-ui` e `/v3/api-docs` respondem 404. `true`: a documentação fica pública, sem token ([api/README.md](api/README.md)). |
| `IMAGE_TAG` | não (padrão `latest`) | Tag inexistente: o `pull` falha e nada muda. |
| `NGINX_MODO` | o script define | `https` sem o certificado: o nginx não sobe. Use o script, que resolve a ordem. |
| `BACKEND_MEM_LIMIT`, `DB_POOL_MAX`, `JOBS_ENABLED`, `APP_TIMEZONE` | não | Padrões do exemplo (640m, 8, true, America/Sao_Paulo). |

## 6. Conferir

Do seu PC, com o repositório do app ao lado (o script roda o smoke do app, que cria uma conta de teste e a exclui):

```powershell
cd C:\Users\arthu\Projetos\medcare-senior-backend\deploy
.\verificar-producao.ps1 -Url https://medcare-senior.duckdns.org
.\verificar-producao.ps1 -Url https://medcare-senior.duckdns.org -TestarLimite -SemSmoke   # confere o limite do nginx
```

O que ele confere:
- saúde `{"status":"UP"}` sem detalhes;
- http:// redirecionando para https://;
- certificado válido e com mais de 14 dias até vencer;
- cabeçalhos de segurança (HSTS, nosniff, frame, CSP; sem a versão do nginx);
- Swagger com 404 e demais endpoints do actuator fechados;
- **portas 5432 e 8080 fechadas para fora**;
- rota da API sem token respondendo 401;
- com `-TestarLimite`, que uma rajada recebe 429 `LIMITE_TENTATIVAS`;
- o fluxo completo do app pelo smoke.

Logs e jobs, na VM:

```bash
cd ~/medcare
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod logs -f --since 10m backend
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod logs backend | grep -E "Verificação de doses|Retentativa de avisos|Reabastecimento de doses"
```

- `Verificação de doses: ...` a cada minuto (doses encerradas e avisos criados ou enviados).
- `Retentativa de avisos: ...` a cada 5 minutos. `Reabastecimento de doses: ...` todo dia às 00:05.

## 7. Cópia de segurança e restauração do banco

`deploy/backup.sh` grava um `pg_dump` comprimido (formato custom) em `~/medcare-backups`, confere se o arquivo é
legível antes de aceitá-lo, deixa a permissão em 600 e apaga as cópias com mais de 14 dias. A senha não aparece:
o dump roda dentro do contêiner do banco.

```bash
cd ~/medcare
./backup.sh                                              # à mão
BACKUP_RETENCAO_DIAS=30 BACKUP_DIR=/caminho ./backup.sh  # outra pasta ou retenção
crontab -l | grep medcare-backup                         # agendado pelo instalar-vm.sh --backup-diario
```

**Leve uma cópia para fora da VM** de vez em quando (a Oracle pode recuperar instâncias Always Free paradas, e um
disco não é backup de si mesmo). Os arquivos têm dados pessoais e de saúde: guarde num lugar protegido, nunca no git.

```powershell
scp -i $HOME\.ssh\medcare_oci "ubuntu@IP_PUBLICO:~/medcare-backups/medcare-*.dump" C:\Users\arthu\segredos\backups\
```

Restaurar (substitui todos os dados atuais; pede para digitar RESTAURAR):

```bash
cd ~/medcare
./restaurar.sh ~/medcare-backups/medcare-20261026-023000.dump
```

O script para o backend, restaura numa transação só (ou volta tudo, ou nada muda) e sobe o backend de novo, mesmo
se der erro. As migrations que vieram no backup são reconhecidas pelo Flyway.

**Testado de verdade** num PostgreSQL descartável: dados criados, backup, schema apagado inteiro, restauração com as
contagens e a versão das migrations idênticas, retenção apagando a cópia velha e arquivo inválido recusado sem tocar
no banco.

### Trazer os dados do desenvolvimento (opcional)

```powershell
# PC: dump do banco de desenvolvimento (usuário e banco do .env de desenvolvimento)
docker exec medcare_postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' > medcare.dump
scp -i $HOME\.ssh\medcare_oci medcare.dump ubuntu@IP_PUBLICO:~/medcare/
```

```bash
# VM
cd ~/medcare && ./restaurar.sh medcare.dump && rm medcare.dump
```

## 8. Atualizar a versão do backend

```bash
cd ~/medcare
nano .env.prod                         # IMAGE_TAG=<SHA curto do commit do main> (ou latest)
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod pull backend
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod up -d backend
```

Para voltar uma versão, use o SHA anterior em `IMAGE_TAG` e repita. Faça um `./backup.sh` antes de atualizar.

## 9. App apontando para a produção

No EAS, ambiente `production`, defina `EXPO_PUBLIC_API_URL=https://SEU_SUBDOMINIO.duckdns.org` (passo a passo no
repositório do app, `docs/apk-de-release.md`). O app de release recusa endereço sem `https://` e não usa tráfego em
texto claro.

## 10. Teste de carga: isentar o IP do gerador de carga do limite do nginx

O nginx limita cada IP a 20 requisições por segundo na API (rajada de 80). Isso derrubaria um teste de carga feito
de uma só máquina. A isenção existe, mas vem **desligada**: `deploy/nginx/carga-isenta.conf` não tem nenhum IP ativo.

```bash
cd ~/medcare
nano nginx/carga-isenta.conf           # descomente e troque o IP de exemplo pelo IP público da máquina do JMeter
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -t
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload
# ... teste de carga (docs/carga/README.md) ...
nano nginx/carga-isenta.conf           # comente a linha de novo
sudo docker compose -f docker-compose.prod.yml --env-file .env.prod exec nginx nginx -s reload
```

### Ensaio local da pilha de produção

O mesmo `docker-compose.prod.yml` foi ensaiado na máquina de desenvolvimento antes de qualquer VM: imagem construída
do Dockerfile, `DOMINIO=localhost`, certificado autoassinado e `verificar-producao.ps1 -IgnorarCertificado
-TestarLimite`. Passaram:
- saúde e redirecionamento para HTTPS;
- cabeçalhos de segurança;
- Swagger com 404, e 200 com `SWAGGER_ENABLED=true`, com a página carregando pela CSP própria;
- 401 sem token;
- o limite do nginx (429 `LIMITE_TENTATIVAS`);
- o smoke completo do app (34 passos) através do nginx.

Só o nginx publica portas (80 e 443); backend e PostgreSQL ficam só na rede interna. O teste de portas acusou a 5432
porque, naquela máquina, ela é o PostgreSQL de desenvolvimento, fora da pilha. Na VM, ele não existe.

## Checklist: pronto para produção

Marque cada item na ordem:

- [ ] VM criada (Ampere A1 ou AMD micro), com IP público anotado e SSH com chave funcionando.
- [ ] Lista de segurança da Oracle com entrada só em 22 (de preferência só do seu IP), 80 e 443. Nada de 5432 nem 8080.
- [ ] Subdomínio do DuckDNS apontando para o IP (`nslookup` confere).
- [ ] Pacote do ghcr público, ou `--ghcr-usuario` com um token só `read:packages`.
- [ ] `instalar-vm.sh` terminou com "Pronto: https://... está no ar".
- [ ] `.env.prod` com permissão 600, `JWT_SECRET` e `POSTGRES_PASSWORD` gerados (nunca os de desenvolvimento) e
      `SWAGGER_ENABLED=false`.
- [ ] Push: `FCM_ENABLED=true` com `segredos/firebase-adminsdk.json` presente (ou `false` sabendo que os avisos não
      chegam).
- [ ] `crontab -l` mostra `medcare-renovacao` e `medcare-backup`.
- [ ] Um backup manual (`./backup.sh`) gerou arquivo, e uma cópia foi levada para fora da VM.
- [ ] `verificar-producao.ps1` passou inteiro, inclusive com `-TestarLimite`.
- [ ] Teste de carga leve contra o servidor (docs/carga/README.md), com a isenção do nginx retirada depois.
- [ ] App de release gerado com `EXPO_PUBLIC_API_URL` apontando para o domínio (repositório do app).

## Plano B: backend no PC com túnel HTTPS

Se a VM não sair (falta de capacidade, cartão recusado), o backend roda no PC e um túnel dá a URL `https://` pública.
Passo a passo em [capacidade.md](capacidade.md#plano-b-backend-local-com-túnel-https).
