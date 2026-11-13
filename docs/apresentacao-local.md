# Apresentação no PC (backend local com túnel HTTPS estável)

Modo **adicional** ao servidor da Oracle ([implantacao.md](implantacao.md)), que continua documentado e sem
mudanças. Aqui o próprio PC serve o backend e o banco, e um túnel ngrok com **domínio estático gratuito** dá a URL
`https://` pública que o APK final usa. Os dois celulares funcionam em qualquer rede (Wi-Fi da banca ou dados
móveis), sem abrir porta no roteador, e o app de release continua aceitando só HTTPS.

## Arquitetura

```text
 Celular do cuidador ─┐                      PC (Docker Desktop, projeto "medcare-apresentacao")
                      │  HTTPS               ┌───────────────────────────────────────────────────────────┐
                      ├────────► ngrok ──────┼─► ngrok (agente) ──rede "borda"──► backend :8080 (prod)  │
 Celular do paciente ─┘  (domínio estático)  │                                       │     ▲            │
                                             │                       rede "banco" ───┘     │ 127.0.0.1:8085│
                                             │                       (interna)       postgres (volume  │
                                             │                                       medcare_apresent..)│
                                             └───────────────────────────────────────────────────────────┘
 Backend ──► Firebase Cloud Messaging (push ao cuidador; conexão de saída, nada a configurar)
```

- **postgres** (`postgres:16-alpine`): volume `medcare_apresentacao_pgdata`, sem porta publicada, 256 MB.
- **backend**: compilado do código local com o `Dockerfile` do repositório (JRE 17, usuário sem privilégios), perfil
  `prod`, heap em 60% de 768 MB, saúde em `/actuator/health`. Porta só em `127.0.0.1:8085`, para checagens no PC
  (não conflita com o IntelliJ na 8080 nem com o Metro na 8081).
- **ngrok** (`ngrok/ngrok:3.39.13-alpine`): termina o HTTPS em `https://<NGROK_DOMAIN>` e repassa ao backend.
- Tudo com `restart: unless-stopped`: depois de reiniciar o PC, basta o Docker Desktop abrir.
- O `medcare_postgres` do desenvolvimento (5432) e o backend do IntelliJ (8080) não são tocados.

### IP do cliente atrás do túnel

Atrás do ngrok todas as conexões chegam do contêiner do ngrok. O perfil `prod` usa
`server.forward-headers-strategy: native`: o Tomcat troca o IP da conexão pelo do `X-Forwarded-For` **apenas quando
a conexão vem de um proxy em rede interna** (o contêiner do ngrok ou o nginx da VM) e pega o último IP da lista, que
é o gravado pelo próprio proxy. Assim:

- cada celular é limitado pelo **próprio IP** no pareamento (5 códigos errados em 15 minutos), e os dois celulares da
  banca nunca se bloqueiam;
- um cliente não escapa do limite inventando um `X-Forwarded-For`: o IP que conta é o que o proxy acrescentou;
- fora do perfil `prod` (desenvolvimento, sem proxy) o cabeçalho é ignorado.

É seguro porque o backend só é alcançável pelo túnel: nenhuma porta pública no host (a 8085 escuta só em
`127.0.0.1`). O teste `IpDoClienteAtrasDoProxyTest` comprova os três pontos com o Tomcat de verdade.

## Pré-requisitos (uma vez)

| O quê | Onde |
|---|---|
| Docker Desktop instalado e aberto | *Settings → General → Start Docker Desktop when you sign in* ligado |
| `ngrok.env` (ou `ngrok.env.txt`) com `NGROK_AUTHTOKEN=` e `NGROK_DOMAIN=` (sem `https://`) | `C:\Users\arthu\segredos\` |
| Credencial do Firebase Admin (`*adminsdk*.json`) | `C:\Users\arthu\segredos\` (sem ela, o push fica desligado) |

Os scripts leem dos arquivos de segredo só o nome e, do ngrok, só a linha `NGROK_DOMAIN`. Outra pasta: defina
`MEDCARE_SEGREDOS` antes de rodar.

## Comandos

Na pasta `deploy\apresentacao` (PowerShell). Todos aceitam `-?` para ajuda.

| Script | O que faz |
|---|---|
| `iniciar-apresentacao.ps1` | Confere o Docker, cria o `.env.apresentacao` na primeira vez (senha do banco e JWT aleatórios, nada na tela), compila e sobe, espera a saúde local e a pública e imprime `PRONTO: https://... \| backend OK \| banco OK \| push OK \| túnel OK`. `-SemBuild` reaproveita a imagem. |
| `preparar-demonstracao.ps1` | Cuidador `banca@exemplo.com`, Dona Maria e Seu José com remédios, 14 dias de histórico, uma dose "ao vivo" daqui a `-MinutosAteAPrimeiraDose` (padrão 4, de 3 a 600; abaixo de 6 o horário é aproximado por SQL, porque a API exige 5 minutos de antecedência), para ver o lembrete tocar no celular do paciente, e códigos de pareamento novos. Idempotente. Salva as credenciais em `C:\Users\arthu\Projetos\CREDENCIAIS_DEMO.txt`. |
| `status-apresentacao.ps1` | Tabela de saúde: contêineres, saúde local e pública, último evento de push, relógio do PC contra o UTC, disco e versão da imagem. |
| `parar-apresentacao.ps1` | Para tudo sem apagar dados. |
| `resetar-demonstracao.ps1` | Apaga **só** o volume do banco de apresentação (pede `APAGAR`) e recria os dados de demonstração. |
| `manter-pc-acordado.ps1` | Na tomada, suspensão e hibernação em "nunca" no plano atual; mostra o comando que reverte. |

O histórico de 14 dias é gravado por SQL direto no contêiner do banco de apresentação, porque a API só cria doses
futuras. Isso existe **somente no script de demonstração**: não há rota nem perfil do servidor que faça isso.

## Modo de teste

O `docker-compose.apresentacao.yml` sobe o backend com `APP_MODO_TESTE=true`, para testar o app no aparelho sem
esperar: a tolerância aceita a partir de **1 minuto** (o app gerado com `EXPO_PUBLIC_MODO_TESTE=true` mostra a opção "1 min")
e o tratamento aceita **qualquer horário**, sem a antecedência mínima de 5 minutos. O log do backend avisa ao subir.
Para voltar às regras normais: `$env:APP_MODO_TESTE="false"` antes de `.\iniciar-apresentacao.ps1` (ou mude o padrão no
compose) e gere o app sem `EXPO_PUBLIC_MODO_TESTE`. Fora da apresentação (`application.yml`), o padrão é desligado.
Com 1 minuto de tolerância, o aviso ao cuidador sai 1 minuto depois do horário; o reforço do servidor ao paciente
(2 minutos depois) não chega a sair, porque vale só até o aviso.

## Atualizar o backend

Depois de mudar o código: `.\iniciar-apresentacao.ps1` (recompila e recria só o backend; os dados ficam). As
migrações novas do Flyway rodam na subida.

## Problemas mais prováveis

| Sintoma | Causa provável | O que fazer |
|---|---|---|
| "O Docker Desktop não está em execução" | Docker fechado ou ainda iniciando | Abra o Docker Desktop, espere ficar verde, rode `iniciar-apresentacao.ps1`. |
| Saúde local OK, pública FALHA | Túnel caiu ou internet do PC instável | `docker compose -p medcare-apresentacao logs --tail 50 ngrok`; `docker compose -p medcare-apresentacao restart ngrok`. Confira a internet do PC. |
| ngrok "endpoint is already online" / "domain not found" | Domínio errado ou o mesmo domínio aberto em outro lugar | Confira `NGROK_DOMAIN` no `ngrok.env` (o domínio do painel do ngrok, sem `https://`); feche outro `ngrok` aberto com esse domínio. |
| Backend reiniciando sem parar, log com erro do Flyway/PostgreSQL | Banco corrompido (desligamento abrupto) | `resetar-demonstracao.ps1` (apaga só o banco de apresentação e recria a demonstração). |
| Doses "atrasadas" erradas, dose ao vivo fora de hora | Relógio do PC errado | `status-apresentacao.ps1` mostra a diferença; *Configurações → Hora e idioma → Sincronizar agora*. |
| `push DESLIGADO` no resumo | Sem `*adminsdk*.json` em `segredos` | Coloque a credencial e rode `iniciar-apresentacao.ps1` de novo. Desligado, os avisos ficam como falha `PUSH_DESLIGADO`. |
| "port is already allocated" na 8085 | Outro programa usando a porta | `netstat -ano \| findstr :8085` e encerre o processo, ou troque a porta no compose e em `comum.ps1`. |
| Backend sai com `OutOfMemoryError` ou o PC fica lento | Memória baixa | Feche o emulador, o IntelliJ e navegadores; o Docker Desktop precisa de uns 1,5 GB livres. `docker stats --no-stream` mostra o consumo. |

## Relação com o servidor da Oracle

Os dois modos usam a mesma imagem e o mesmo perfil `prod`. O app de release aponta para um só endereço, definido na
build (`EXPO_PUBLIC_API_URL`): o APK final da apresentação aponta para o domínio do ngrok.
