# E-mail do cuidador: confirmação e troca de senha

Até aqui o e-mail era só o nome de usuário do login: o servidor não enviava nada a ele e aceitava qualquer endereço
com `@` (o `@Email` do Java aceita até `a@b`). Quem esquecia a senha perdia a conta, e qualquer pessoa podia
cadastrar o e-mail de outra.

Agora:

| O quê | Como |
|---|---|
| **Formato** | O mesmo do app: `algo@dominio.ext`, sem espaços e com ponto no domínio (`FormatoDeEmail`) |
| **Domínio existe** | No cadastro, uma consulta MX no DNS: domínio que não existe ("hotmial.com.br") responde `400 EMAIL_INVALIDO` com a explicação. Falha aberta: DNS fora do ar ou lento não bloqueia o cadastro |
| **Confirmação** | A conta nasce com `emailVerificado=false` e um código de 6 dígitos vai para o e-mail. Sem confirmar, o cuidador entra no app, mas **não cadastra pacientes** (`403 EMAIL_NAO_VERIFICADO`) |
| **Esqueci minha senha** | `POST /api/auth/esqueci-senha` envia um código; `POST /api/auth/redefinir-senha` troca a senha com ele (e confirma o e-mail) |

Contas que já existiam antes da V20 (e as criadas por SQL, como a da demonstração) contam como confirmadas.

## Rotas

| Rota | Perfil | O que faz |
|---|---|---|
| `POST /api/cuidadores` | público | Cadastro; responde `emailVerificado: false` e envia o código |
| `POST /api/auth/login` | público | Responde também `emailVerificado` (o app manda para a tela do código) |
| `POST /api/cuidadores/me/verificacao-email/confirmar` | CUIDADOR | `{ "codigo": "482915" }` → 200 com o cuidador confirmado |
| `POST /api/cuidadores/me/verificacao-email/reenviar` | CUIDADOR | Outro código (204); antes de 1 minuto, `429 LIMITE_TENTATIVAS` com `Retry-After` |
| `POST /api/auth/esqueci-senha` | público | `{ "email": ... }` → sempre 204, com ou sem conta (não revela quais e-mails existem) |
| `POST /api/auth/redefinir-senha` | público | `{ "email", "codigo", "novaSenha" }` → 204; código errado `400 CODIGO_EMAIL_INVALIDO` |

## Regras do código (`CodigosPorEmailService`)

- 6 dígitos (`SecureRandom`), válido por **15 minutos**, uso único; um código novo substitui o anterior.
- Outro código para a mesma conta e finalidade só depois de **1 minuto**.
- **5 códigos errados** invalidam o código (a mensagem diz quantas tentativas restam); é preciso pedir outro.
- Só o **hash SHA-256** fica no banco (tabela `codigo_email`, um por cuidador e finalidade), comparado em tempo
  constante. Excluir a conta apaga os códigos (`ON DELETE CASCADE`).
- Limites por IP (o mesmo `RateLimiter` do pareamento, com contadores próprios): 5 pedidos de "esqueci minha senha" e 5
  códigos errados na troca de senha a cada 15 minutos.
- Na troca de senha, e-mail sem conta responde igual a código inválido.

## Configurar o envio

Desligado por padrão (`EMAIL_ENABLED=false`): o código não sai do servidor. Com `EMAIL_LOG_DO_CODIGO=true`, ele aparece
no log, para testar sem SMTP. **Nunca** use isso num servidor de verdade: quem lê o log confirma qualquer conta.

| Variável | Exemplo |
|---|---|
| `EMAIL_ENABLED` | `true` |
| `EMAIL_REMETENTE` | `MedCare Sênior <suaconta@gmail.com>` |
| `SMTP_HOST` / `SMTP_PORT` | `smtp.gmail.com` / `587` (STARTTLS obrigatório) |
| `SMTP_USUARIO` / `SMTP_SENHA` | a conta e a senha de app (nunca no repositório) |
| `EMAIL_VERIFICAR_DOMINIO` | `true` (padrão); `false` desliga a consulta ao DNS |

Qualquer provedor SMTP serve:

- **Gmail:** ative a verificação em duas etapas na conta Google, crie uma *senha de app* em
  <https://myaccount.google.com/apppasswords> e use-a em `SMTP_SENHA`. Limite de cerca de 500 e-mails por dia.
- **Brevo / Resend / outros:** use o host, a porta 587 e o usuário e a senha SMTP que o painel do serviço mostra; o
  remetente precisa ser um endereço ou domínio validado lá.

O SMTP fora do ar não derruba a saúde do contêiner (`management.health.mail.enabled=false`): o envio só falha, fica no
log (sem o código nem o endereço completo) e o cuidador pede outro código no app. Na apresentação no PC, o arquivo
`smtp.env` da pasta de segredos liga o envio ([apresentacao-local.md](apresentacao-local.md#e-mail)).

## Limites conhecidos

- Trocar a senha não derruba sessões já abertas em outros aparelhos (o JWT vale até expirar, 24 horas).
- O cadastro espera o envio do e-mail (1 a 3 segundos no SMTP). Se falhar, a conta fica criada e o app oferece
  "Reenviar código".
