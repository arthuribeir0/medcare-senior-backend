# LGPD — Política de exclusão de dados

Documento técnico da política de exclusão de dados do MedCare Sênior.

## Dois tipos de remoção

| Operação | Tipo | Quem usa | Implementação |
|---|---|---|---|
| Remover um medicamento | **Lógica** (preenche `removido_em`) | Cuidador, no dia a dia | `RemoverMedicamentoUseCase` |
| Excluir a conta | **Física** (apaga as linhas) + anonimização das doses registradas | Cuidador, direito do titular (LGPD, art. 18, VI) | `ExcluirContaUseCase` |

### Remoção lógica de medicamento e tratamento

O histórico de doses registradas é imutável (RN-008) e depende do tratamento, que depende do medicamento. Por
isso, "remover" um medicamento **não apaga** nada do histórico:

- o medicamento e todos os tratamentos dele recebem `removido_em` = agora, na mesma transação;
- as doses **PENDENTES futuras** desses tratamentos são apagadas (nunca foram registradas);
- as doses passadas e as já registradas ficam intactas;
- itens removidos ficam no banco, mas são invisíveis: todas as consultas os ignoram e a API responde 404;
- tratamento removido não gera mais doses.

A remoção lógica **não é** uma forma de reter dados pessoais contra a vontade do titular: quem quer apagar os
dados exclui a conta, e aí tudo é apagado de fato (abaixo).

## Exclusão de conta

Exposta em `DELETE /api/cuidadores/me` (somente o próprio cuidador, papel CUIDADOR; resposta 204). É
**definitiva** e acontece em **uma única transação**: ou todos os dados são removidos, ou nada muda.

| Dado | Tratamento |
|---|---|
| Cuidador (nome, e-mail, senha, telefone, consentimento, **token de dispositivo**) | Apagado |
| Idosos do cuidador | Apagados |
| Medicamentos dos idosos, **inclusive os removidos logicamente** | Apagados |
| Tratamentos dos medicamentos, **inclusive os removidos logicamente** | Apagados |
| Códigos de vínculo | Apagados |
| Mensagens das conversas com os idosos do cuidador | Apagadas |
| Doses PENDENTES (passadas e futuras) | Apagadas |
| Doses registradas (CONFIRMADA_NO_HORARIO, NAO_TOMADA, CONFIRMADA_COM_ATRASO) | **Anonimizadas**: mantidas com `tratamento_id = NULL` |

Ordem de remoção (respeita as chaves estrangeiras): códigos de vínculo → eventos de notificação → doses pendentes →
tratamentos → medicamentos → mensagens → idosos → cuidador. A chave estrangeira `mensagem.idoso_id` também tem
`ON DELETE CASCADE`, como garantia.

## Mensagens

O conteúdo das mensagens entre cuidador e idoso (`mensagem.conteudo`) é dado pessoal: nunca é escrito em log e só é
devolvido aos dois lados da conversa (o cuidador dono do idoso e o próprio idoso). Na exclusão da conta, as mensagens
de todos os idosos do cuidador são apagadas fisicamente (coberto por
`MensagemPostgresTest.exclusaoDeContaApagaAsMensagensDosIdososDoCuidadorSemQuebrarPorFk`).

### Por que as doses registradas são anonimizadas, e não apagadas

O histórico de doses tem valor estatístico (adesão a tratamentos), mas não precisa identificar ninguém. A coluna
`dose.tratamento_id` tem chave estrangeira com `ON DELETE SET NULL`: quando o tratamento é apagado, o banco zera
o vínculo. A dose restante guarda só horário programado, hora de confirmação e status — sem tratamento,
medicamento, idoso ou cuidador. Dado anonimizado de forma irreversível deixa de ser dado pessoal (LGPD, art. 12).

## Token de dispositivo (push)

O token FCM do aparelho do cuidador (`cuidador.fcm_token`, registrado em `PUT /api/cuidadores/me/fcm-token`) é
**dado pessoal**: identifica um aparelho de uma pessoa e permite enviar mensagens a ele.

- Fica **só** na linha do cuidador, sem cópia em outra tabela. Na exclusão de conta ele é apagado junto com essa
  linha (coberto por `PersistenciaPostgresTest.exclusaoDeContaApagaOTokenDeDispositivo`).
- Um aparelho pertence a **uma conta por vez**: ao registrar um token, ele é retirado de qualquer outro cuidador
  que o tenha. Sem isso, quem trocasse de conta no mesmo celular continuaria recebendo avisos da conta anterior,
  que trazem nome de idoso e de medicamento.
- Nunca é escrito em log; os logs de envio de aviso trazem apenas IDs (dose e cuidador).
- O conteúdo do aviso (nome do idoso, do medicamento e horário) passa pelo Firebase Cloud Messaging (Google) para
  chegar ao aparelho. Isso é tratamento de dados por operador e precisa constar no termo de consentimento antes de
  ligar o envio real (`app.fcm.enabled=true`) em produção.

## Tokens emitidos antes da exclusão

O JWT é *stateless*: um token emitido antes da exclusão continua com assinatura válida até expirar
(24 horas para o cuidador). Isso não expõe dados, porque todas as rotas buscam os recursos filtrando pelo
cuidador do token, que não existe mais:

- `DELETE /api/cuidadores/me` de novo → 404;
- rotas de idosos, medicamentos e tratamentos → 404 (o recurso não é encontrado para aquele cuidador);
- os IDs são `BIGSERIAL` e nunca são reutilizados, então o token antigo nunca passa a apontar para outra conta.

Tokens de **idosos** pareados com essa conta também continuam válidos até expirar (180 dias), mas hoje não há
nenhuma rota que aceite o papel IDOSO. **Quando houver** (ex.: confirmação de dose), essas rotas precisam tratar o
idoso inexistente como 404 (ou passar a verificar a existência do usuário no filtro de autenticação e responder 401).

## Consentimento

O cadastro do cuidador só é aceito com `consentimentoLgpd = true`; a data e hora do aceite são gravadas em
`cuidador.data_consentimento`. O texto do termo está em [termo-consentimento-lgpd.md](termo-consentimento-lgpd.md).

**Limitação conhecida:** cuidadores cadastrados antes da migration V5 ficaram com `consentimento_lgpd = false` e
continuam conseguindo fazer login; não há fluxo de novo aceite. Hoje só existem contas de teste; antes de
produção, é preciso decidir entre exigir o aceite no próximo login ou apagar essas contas.
