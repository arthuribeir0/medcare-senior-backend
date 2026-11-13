-- Lembrete de dose no aparelho do paciente (UC-006, passo 1): token do aparelho do paciente e tipo do evento de
-- notificação, para o servidor reforçar o lembrete que o próprio aparelho agenda.

-- Token de dispositivo (Firebase Cloud Messaging) do aparelho do paciente, gravado pelo app depois do pareamento e a
-- cada abertura. token_atualizado_em diz quando o app o registrou pela última vez.
-- Dado pessoal (LGPD): apagado quando o paciente sai da conta, quando o aparelho é desconectado e quando o provedor diz
-- que o token não vale mais; some junto com a linha na desvinculação do paciente e na exclusão da conta do cuidador.
-- NULL = sem aparelho registrado (o lembrete do servidor não é enviado).
ALTER TABLE paciente
    ADD COLUMN token_fcm VARCHAR(512) NULL,
    ADD COLUMN token_atualizado_em TIMESTAMPTZ NULL;

-- Token e data andam juntos
ALTER TABLE paciente
    ADD CONSTRAINT ck_paciente_token_fcm CHECK ((token_fcm IS NULL) = (token_atualizado_em IS NULL));

-- Um aparelho pertence a um paciente por vez: o registro tira o mesmo token de qualquer outro paciente. Parcial: quase
-- todas as linhas sem aparelho ficam fora do índice.
CREATE INDEX idx_paciente_token_fcm ON paciente (token_fcm) WHERE token_fcm IS NOT NULL;

-- Tipo do evento de notificação. Os eventos que já existem são todos avisos ao cuidador.
--   AVISO_CUIDADOR    = dose não confirmada dentro da tolerância (RN-009, RN-010)
--   LEMBRETE_PACIENTE = reforço do lembrete da dose, enviado ao aparelho do paciente
-- O valor padrão serve só para preencher as linhas existentes; depois é retirado, para todo INSERT dizer o tipo.
ALTER TABLE evento_notificacao
    ADD COLUMN tipo VARCHAR(30) NOT NULL DEFAULT 'AVISO_CUIDADOR';
ALTER TABLE evento_notificacao
    ALTER COLUMN tipo DROP DEFAULT;
ALTER TABLE evento_notificacao
    ADD CONSTRAINT ck_evento_notificacao_tipo CHECK (tipo IN ('AVISO_CUIDADOR', 'LEMBRETE_PACIENTE'));

-- A idempotência passa a ser por dose E tipo: no máximo um aviso ao cuidador e um lembrete ao paciente por dose
-- (INSERT ... ON CONFLICT (dose_id, tipo) DO NOTHING). dose_id continua na frente do índice, então as buscas por dose
-- seguem usando-o. Na mesma transação da migração: não há instante sem unicidade.
ALTER TABLE evento_notificacao
    DROP CONSTRAINT uk_evento_notificacao_dose;
ALTER TABLE evento_notificacao
    ADD CONSTRAINT uk_evento_notificacao_dose_tipo UNIQUE (dose_id, tipo);
