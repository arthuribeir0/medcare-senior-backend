# MedCare Sênior — backend

API do MedCare Sênior: o cuidador cadastra pacientes, remédios e tratamentos; o aparelho do paciente, pareado por código,
mostra as doses do dia e confirma cada uma; o cuidador recebe um aviso (push) quando uma dose não é confirmada a tempo,
acompanha o relatório do dia, o histórico e o PDF de adesão, e troca mensagens com o paciente.

Java 17, Spring Boot 4, PostgreSQL 16 (Flyway), JWT, Firebase Cloud Messaging, OpenPDF, Caffeine. Arquitetura em
camadas: `domain` (regras puras), `application` (casos de uso), `infrastructure` (banco, push, PDF, segurança, jobs) e
`presentation` (controllers e DTOs).

## Rodar no PC

```powershell
docker compose up -d                        # PostgreSQL local (usa o .env: POSTGRES_USER, POSTGRES_PASSWORD, POSTGRES_DB)
.\mvnw.cmd spring-boot:run                  # API em http://localhost:8080
```

Testes (precisam do PostgreSQL local e das variáveis `POSTGRES_*` do `.env` no ambiente; JDK 17):

```powershell
Get-Content .env | ForEach-Object { if ($_ -match '^([^#=]+)=(.*)$') { Set-Item "env:$($Matches[1])" $Matches[2] } }
.\mvnw.cmd verify                           # testes + cobertura do JaCoCo (mínimo de 60% em domain e application)
```

Swagger: `DOCS_ENABLED=true` e <http://localhost:8080/swagger-ui.html>.

## Regras principais

**Janela da dose** (calculada no servidor, `PoliticaDeJanela`; o app só exibe):

| Momento | Quando | O que acontece |
|---|---|---|
| abre | horário − 60 min | a dose já pode ser confirmada |
| aviso | horário + tolerância do cuidador (5 a 120 min, padrão 30) | até aqui (inclusive) a confirmação é **no horário**; depois, **com atraso**; o cuidador recebe o push |
| encerra (`limiteConfirmacao`) | aviso + 30 min (`ALERTAS_MARGEM_POS_AVISO_MINUTOS`) | último instante para confirmar (inclusive); depois a dose vira `NAO_TOMADA` |

Exatamente no encerramento a confirmação ainda é aceita e o job já pode encerrar: quem gravar primeiro vence (UPDATE
atômico condicionado a `status = 'PENDENTE'`).

**Status da dose**: `PENDENTE`, `CONFIRMADA_NO_HORARIO` ("Confirmada no horário"), `CONFIRMADA_COM_ATRASO` e
`NAO_TOMADA` ("Não confirmada"). Dose registrada nunca muda (RN-008).

**Quem confirma**: o paciente (as próprias doses) e o cuidador (doses dos pacientes dele), em
`PUT /api/doses/{id}/confirmar`, com as mesmas regras. A dose guarda `confirmadoPor` (`PACIENTE` ou `CUIDADOR`). Dose de
paciente de outro cuidador responde o mesmo 404 de dose inexistente.

**Aviso ao cuidador**: push pelo FCM sem o nome do remédio (LGPD), um por dose, com retentativa para falhas
temporárias. Com `FCM_ENABLED=false` o aviso fica registrado como falha com motivo `PUSH_DESLIGADO`, sem retentativa.

**Relatórios**: o relatório diário fica 30 s em cache e é descartado na hora quando uma dose do paciente é confirmada ou
encerrada (ou quando o cuidador muda tolerância, remédio ou tratamento). O PDF (`GET /api/relatorios/pdf`) aceita
período de até 12 meses (acima disso, 400 `VALIDACAO`); período sem doses é 422 `PERIODO_SEM_REGISTROS`.

**Geração de doses**: janela rolante de 30 dias à frente (`DOSES_HORIZONTE_DIAS`, de 1 a 90), idempotente.

## Variáveis de ambiente mais usadas

| Variável | Padrão | Para quê |
|---|---|---|
| `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | localhost, 5432, medcare_db | Banco |
| `DB_POOL_MAX`, `DB_POOL_MIN_IDLE` | 8, 2 | Pool de conexões (ver `docs/capacidade.md`) |
| `JWT_SECRET` | valor de desenvolvimento | Obrigatório e forte em produção |
| `APP_TIMEZONE` | America/Sao_Paulo | "Hoje" e "agora" da aplicação |
| `ALERTAS_TOLERANCIA_MINUTOS` | 30 | Tolerância de quem não configurou a sua |
| `ALERTAS_MARGEM_POS_AVISO_MINUTOS` | 30 | Encerramento = aviso + margem |
| `ALERTAS_ANTECEDENCIA_CONFIRMACAO_MINUTOS` | 60 | Quanto antes do horário a dose já pode ser confirmada |
| `DOSES_HORIZONTE_DIAS` | 30 | Dias de doses geradas à frente |
| `CACHE_RELATORIO_DIARIO_SEGUNDOS` | 30 | Cache do relatório diário (0 desliga) |
| `FCM_ENABLED`, `FCM_DRY_RUN`, `FIREBASE_CREDENTIALS_PATH` | false, false, vazio | Push |
| `JOBS_ENABLED` | true | Varredura de doses, retentativa e reabastecimento |
| `DOCS_ENABLED` | false | Swagger |

## Documentação

- [docs/implantacao.md](docs/implantacao.md) — implantação na Oracle Cloud com Docker Compose, nginx e HTTPS (inclui a
  isenção do limite do nginx para o teste de carga).
- [docs/capacidade.md](docs/capacidade.md) — capacidade estimada, pool de conexões, planos das consultas e cache.
- [docs/lgpd.md](docs/lgpd.md) — dados pessoais, Google/FCM como operador, retenção e exclusão de conta.
- [docs/termo-consentimento-lgpd.md](docs/termo-consentimento-lgpd.md) — texto do termo mostrado no cadastro.
