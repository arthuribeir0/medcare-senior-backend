# Documentação da API

O contrato completo está em [`openapi.json`](openapi.json) (OpenAPI 3.1), gerado a partir do código. Com o backend
rodando e a documentação ligada, o mesmo conteúdo aparece em:

- Swagger UI: <http://localhost:8080/swagger-ui/index.html>
- JSON: <http://localhost:8080/v3/api-docs>

## O que cada operação traz

- **Resumo e descrição** em português, começando pelo **perfil exigido**: público, CUIDADOR, PACIENTE ou CUIDADOR ou
  PACIENTE.
- **Cadeado** (`bearerAuth`, JWT) só nas rotas protegidas. As rotas públicas são o cadastro do cuidador, o login, o
  pareamento por código e a saúde.
- **Parâmetros** com exemplo e restrições (tamanho de página de 1 a 100, datas `AAAA-MM-DD`, tamanhos máximos dos
  campos nos esquemas).
- **Todas as respostas**, inclusive as de erro, sempre com o corpo `ErroResponse` e um exemplo para cada código que a
  resposta pode trazer.

Agrupamento por tag: Autenticação, Cuidador, Pacientes, Vínculo, Medicamentos e tratamentos, Doses, Configurações,
Relatórios, Histórico, Mensagens, Push/Token e Saúde.

## Erros

Toda resposta de erro tem o mesmo corpo:

```json
{
  "timestamp": "2026-10-08T14:30:00",
  "status": 409,
  "erro": "Conflict",
  "codigo": "DOSE_JA_REGISTRADA",
  "mensagem": "Esta dose já foi registrada.",
  "campos": []
}
```

O app decide pelo `codigo`, que nunca muda de nome; a `mensagem` é para pessoas. A tabela completa dos códigos está em
`CodigoErro.java`. Os principais:

| HTTP | Códigos |
|---|---|
| 400 | `VALIDACAO` (com `campos`), `CORPO_INVALIDO`, `REQUISICAO_INVALIDA`, `SENHA_FRACA`, `CONSENTIMENTO_OBRIGATORIO`, `CODIGO_INVALIDO`, `DADOS_OBRIGATORIOS_MEDICAMENTO`, `TRATAMENTO_INVALIDO`, `TOKEN_DISPOSITIVO_INVALIDO` |
| 401 | `NAO_AUTENTICADO` (sem token, token inválido ou expirado), `SESSAO_INVALIDA` (conta do token não existe mais), `CREDENCIAIS_INVALIDAS` (login) |
| 403 | `ACESSO_NEGADO` (token de outro perfil, ou paciente tentando outra conversa) |
| 404 | `RECURSO_NAO_ENCONTRADO` (inexistente, removido ou de outro dono, sem distinção) |
| 409 | `EMAIL_DUPLICADO`, `PACIENTE_DUPLICADO`, `TRATAMENTO_ATIVO`, `DOSE_JA_REGISTRADA`, `DOSE_MUITO_CEDO`, `DOSE_ENCERRADA` |
| 422 | `PERIODO_SEM_REGISTROS` (PDF de período sem doses) |
| 429 | `LIMITE_TENTATIVAS`, com o cabeçalho `Retry-After` |
| 500 | `ERRO_INTERNO`, sem detalhes na resposta |

## Quando a documentação fica exposta

| Ambiente | Variável `SWAGGER_ENABLED` | Resultado |
|---|---|---|
| Fora do perfil `prod` (desenvolvimento, testes) | ausente | **ligada** |
| Fora do perfil `prod` | `false` | desligada |
| Perfil `prod` | ausente ou `false` | **desligada**: `/swagger-ui` e `/v3/api-docs` respondem 404 |
| Perfil `prod` | `true` | ligada |

O nome antigo `DOCS_ENABLED` ainda é aceito fora do perfil `prod`. No `prod` ele é ignorado, para que um valor
esquecido não exponha a documentação.

**Decisão de segurança.** Quando ligada, a documentação é **pública, sem token**. Ela descreve as rotas e os formatos,
mas não dá acesso a nenhum dado: toda rota da API continua exigindo o token do perfil certo, e isso é conferido por
teste. Mesmo assim, em produção ela fica desligada por padrão, para não facilitar o reconhecimento da API. Ligue
(`SWAGGER_ENABLED=true` no `deploy/.env.prod` e `docker compose ... up -d backend`) só quando precisar mostrá-la, e
desligue depois. O nginx de produção encaminha a documentação ao backend com uma CSP própria para a página do
Swagger UI. Com a variável desligada, a resposta é 404.

Testes: `SwaggerDesligadoEmProducaoTest` (404 no `prod` sem a variável, mesmo com `DOCS_ENABLED=true`) e
`SwaggerLigadoEmProducaoTest` (200 sem token com a variável ligada; a API continua com 401).

## Garantias automáticas

O `OpenApiContratoTest` falha quando:

- alguma operação fica sem resumo, sem perfil exigido, sem tag da lista ou sem resposta de erro documentada;
- alguma resposta 4xx/5xx sai sem o corpo `ErroResponse`;
- o cadeado não bate com o perfil anotado (`@PerfilExigido`);
- o perfil anotado não bate com o `SecurityConfig`: o teste chama cada rota sem token e com o token do outro perfil e
  confere 401 `NAO_AUTENTICADO` e 403 `ACESSO_NEGADO`;
- a lista de códigos publicada difere de `CodigoErro`;
- este `openapi.json` fica desatualizado.

Para regerar o arquivo depois de mudar a API (com as variáveis `POSTGRES_*` carregadas, como nos outros testes):

```powershell
.\mvnw.cmd test -Dtest=OpenApiContratoTest -Dopenapi.atualizar=true
```
