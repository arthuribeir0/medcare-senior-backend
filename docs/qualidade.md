# Qualidade: SonarQube e cobertura

## Resultado

Análise do SonarQube Community 26.10 (perfil "Sonar way", quality gate "Sonar way"), na máquina local:

| Métrica | Antes das correções | Depois |
|---|---:|---:|
| Quality gate | aprovado | **aprovado** |
| Bugs | 2 | **0** |
| Vulnerabilidades | 1 | **0** |
| Security hotspots | 0 | **0** (nada a revisar) |
| Code smells | 1 | **0** |
| Nota de confiabilidade / segurança / manutenibilidade | C / D / A | **A / A / A** |
| Duplicação | 0,0% | 0,0% |
| Cobertura (Sonar, sem as exclusões abaixo) | 94,0% | 94,1% |
| Linhas analisadas | 7.188 | 7.206 |

O que foi corrigido:

| Regra | Onde | Correção |
|---|---|---|
| `java:S2259`, bug: possível `NullPointerException` | `EnvioDeAvisoService.enviar` | O resultado do envio agora vem de um método que nunca devolve nulo (exceção ou resposta vazia viram falha temporária). Já não quebrava, mas o fluxo ficou explícito. |
| `java:S6218`, bug: record com array compara pela referência | `GerarRelatorioPdfUseCase.Resultado` | `equals` e `hashCode` pelo conteúdo do PDF; `toString` sem dados de saúde (só o id do paciente e o tamanho do PDF). Com teste. |
| `java:S6829`, code smell: dois construtores sem indicar qual o Spring usa | `RateLimiter` | `@Autowired` no construtor público (o outro é o dos testes, com relógio controlado). |
| `java:S4502`, vulnerabilidade: CSRF desligado | `SecurityConfig` | **Revisada e mantida, com justificativa no código** (`@SuppressWarnings("java:S4502")`). A API é stateless e só aceita o token no cabeçalho `Authorization`, que o navegador não envia sozinho. Não há cookie de sessão nem formulário, então não existe credencial automática para um site de terceiros explorar. Ligar o CSRF exigiria um token anti-CSRF sem proteger nada a mais. |

Nenhum security hotspot foi levantado.

### Exclusões da cobertura (`sonar.coverage.exclusions` no `pom.xml`)

Essas classes saem **só do cálculo de cobertura**. Bugs, vulnerabilidades e code smells continuam sendo analisados
nelas.

| Exclusão | Por quê |
|---|---|
| `presentation/dto/**` | Records de entrada e saída, sem lógica (só os campos e as anotações de validação e documentação, testadas pelos testes de API). |
| `infrastructure/config/*Config.java`, `ShedLockConfig.java` | Declaração de beans do Spring, exercitada por subir o contexto nos testes de integração. |
| `SeniorApplication.java` | O `main`. |

### JaCoCo (o limite do build não mudou)

O `mvnw verify` continua falhando se a cobertura de linhas de `domain` e `application` ficar abaixo de 60%. Medido
neste bloco: 99,6% de linhas e 97,6% de ramos em domain+application; 93% de linhas e 93% de ramos no total. O
projeto não tem Checkstyle nem verificador de arquitetura configurados.

## Como rodar a análise

Precisa de Docker e das variáveis `POSTGRES_*` (os testes rodam no `verify`). O mesmo SonarQube serve ao repositório
do app.

1. **Subir o SonarQube** (só em `127.0.0.1:9000`; o primeiro início leva cerca de 1 minuto):
   ```powershell
   docker compose -f docker-compose.sonar.yml up -d
   ```
2. **Primeiro acesso:** abra <http://localhost:9000>, entre com `admin` / `admin` e troque a senha quando ele pedir.
3. **Criar o token:** canto superior direito → *My Account* → *Security* → *Generate Tokens*. Escolha o tipo *Global
   Analysis Token* (serve aos dois projetos) e copie o valor. Ele não aparece de novo, e **não vai para nenhum
   arquivo do repositório**.
4. **Analisar** (testes, cobertura e envio; o resultado aparece em *Projects* → *MedCare Sênior - backend*):
   ```powershell
   $env:SONAR_TOKEN = "cole-o-token-aqui"
   .\mvnw.cmd verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
   ```
5. **Derrubar** ao terminar:
   ```powershell
   docker compose -f docker-compose.sonar.yml down      # mantém projetos e token para a próxima vez
   docker compose -f docker-compose.sonar.yml down -v   # apaga tudo
   ```

## No CI (opcional)

O job `qualidade` do `backend-ci.yml` analisa cada push e pull request do `main`, **só se** o repositório tiver a
variável `SONAR_HOST_URL` e o segredo `SONAR_TOKEN`. Sem eles, o job é pulado e o CI segue verde. Se a análise falhar
(servidor fora do ar, gate reprovado), o job aparece com aviso, mas não derruba o CI.

O servidor precisa ser acessível pelo GitHub (o SonarQube local em `localhost` não serve). Pode ser o SonarQube Cloud
(gratuito para repositório público) ou um SonarQube publicado. Para configurar:

1. No servidor, crie o projeto com a chave `medcare-senior-backend` e gere um token de análise.
2. No GitHub: repositório → *Settings* → *Secrets and variables* → *Actions*:
   - aba *Variables* → *New repository variable*: `SONAR_HOST_URL` = endereço do servidor (ex.: `https://sonarcloud.io`);
   - aba *Secrets* → *New repository secret*: `SONAR_TOKEN` = o token.
3. No SonarQube Cloud, informe também a organização: acrescente
   `<sonar.organization>sua-organizacao</sonar.organization>` às propriedades do `pom.xml`.
