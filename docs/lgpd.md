# LGPD — Tratamento e exclusão de dados

Documento técnico do tratamento de dados pessoais do MedCare Sênior: o que é guardado, para quê, com quem é
compartilhado, por quanto tempo e como é apagado. O texto mostrado ao usuário está em
[termo-consentimento-lgpd.md](termo-consentimento-lgpd.md) (o app mostra o mesmo conteúdo na tela de termos).

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
| Eventos de notificação (avisos de dose não confirmada) | Apagados |
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
o vínculo. A dose restante guarda só horário programado, hora de confirmação, status e quem confirmou (`IDOSO` ou
`CUIDADOR`, o papel, nunca a pessoa) — sem tratamento, medicamento, idoso ou cuidador. Dado anonimizado de forma irreversível deixa de ser dado pessoal (LGPD, art. 12).

## Notificações push: Google (Firebase Cloud Messaging) como operador

O aviso de dose não confirmada chega ao aparelho do cuidador pelo **Firebase Cloud Messaging (FCM), serviço do
Google**. Para a LGPD, o Google é **operador** (trata dados em nome do MedCare Sênior, art. 5º, VII, e art. 39): recebe
o token do aparelho e o conteúdo do aviso só para entregá-lo. Os servidores do Google podem ficar fora do Brasil
(transferência internacional, art. 33), o que consta no termo de consentimento.

| Item | Como é |
|---|---|
| Dado pessoal | O **token FCM** do aparelho do cuidador (`cuidador.fcm_token`): identifica um aparelho de uma pessoa e permite enviar mensagens a ele |
| Finalidade única | Avisar o cuidador de que uma dose não foi confirmada até o fim da tolerância (RN-009) |
| O que vai ao Google | Título "Dose não confirmada"; texto "{nome do idoso} ainda não confirmou a dose das HH:mm" (com a data quando não é de hoje); dados para o app abrir a dose: `tipo`, `doseId`, `idosoId` e `data` |
| O que **não** vai | Nome do remédio, dosagem, cor, tipo ou qualquer outro dado de saúde; e-mail, telefone e data de nascimento |
| Retenção do token | Enquanto a conta existir e o app estiver instalado. É trocado quando o app recebe um token novo, apagado na saída da conta no app (`DELETE /api/cuidadores/me/fcm-token`), apagado quando o FCM o recusa como inválido e apagado na exclusão da conta |
| Retenção dos avisos | O evento de cada aviso (`evento_notificacao`: status, tentativas, motivo da falha) fica até a exclusão da conta, quando é apagado |
| Push desligado | Com `FCM_ENABLED=false` nada sai para o Google: o aviso vai só para o log do servidor (com IDs, sem nomes) e o evento é gravado como falha com motivo `PUSH_DESLIGADO` |

Outras garantias:

- O token fica **só** na linha do cuidador, sem cópia em outra tabela (exclusão coberta por
  `PersistenciaPostgresTest.exclusaoDeContaApagaOTokenDeDispositivo`).
- Um aparelho pertence a **uma conta por vez**: ao registrar um token, ele é retirado de qualquer outro cuidador
  que o tenha. Sem isso, quem trocasse de conta no mesmo celular continuaria recebendo avisos da conta anterior,
  com nomes de idosos.
- Nunca é escrito em log; os logs de envio de aviso trazem apenas IDs (dose, idoso e cuidador).
- O app do idoso não registra token: só o cuidador recebe push.

## Tokens emitidos antes da exclusão

O JWT é *stateless*: um token emitido antes da exclusão continua com assinatura válida até expirar
(24 horas para o cuidador). Isso não expõe dados, porque todas as rotas buscam os recursos filtrando pelo
cuidador do token, que não existe mais:

- `DELETE /api/cuidadores/me` de novo → 404;
- rotas de idosos, medicamentos e tratamentos → 404 (o recurso não é encontrado para aquele cuidador);
- os IDs são `BIGSERIAL` e nunca são reutilizados, então o token antigo nunca passa a apontar para outra conta.

Tokens de **idosos** pareados com essa conta também continuam com assinatura válida até expirar (180 dias), mas as
rotas do idoso (dados do próprio idoso, doses do dia, confirmação e mensagens) conferem se o idoso ainda existe e
respondem 401 `SESSAO_INVALIDA`; o app então volta à tela de pareamento. Tokens de cuidador de conta excluída também
recebem 401 `SESSAO_INVALIDA` em qualquer rota.

## Consentimento

O cadastro do cuidador só é aceito com `consentimentoLgpd = true`; a data e hora do aceite são gravadas em
`cuidador.data_consentimento`. O texto do termo está em [termo-consentimento-lgpd.md](termo-consentimento-lgpd.md).

**Limitação conhecida:** cuidadores cadastrados antes da migration V5 ficaram com `consentimento_lgpd = false` e
continuam conseguindo fazer login; não há fluxo de novo aceite. Hoje só existem contas de teste; antes de
produção, é preciso decidir entre exigir o aceite no próximo login ou apagar essas contas.

## Quem confirmou a dose

Desde a V15 a dose confirmada guarda quem confirmou (`dose.confirmado_por`: `IDOSO` ou `CUIDADOR`). É o papel, não
a pessoa: na dose anonimizada continua sem identificar ninguém. Aparece para o cuidador na dose, no histórico, no
relatório diário e no PDF.
