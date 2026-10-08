-- Dose: cada horário programado de um tratamento (RN-008).
-- Doses já registradas (TOMADA, NAO_TOMADA, CONFIRMADA_COM_ATRASO) são imutáveis e formam o histórico de adesão.
-- tratamento_id NULL = dose anonimizada: o tratamento foi apagado na exclusão de conta (LGPD) e a dose perdeu
-- o vínculo com qualquer pessoa. Por isso a FK é ON DELETE SET NULL, e não CASCADE.
CREATE TABLE dose (
                      id BIGSERIAL PRIMARY KEY,
                      tratamento_id BIGINT NULL REFERENCES tratamento (id) ON DELETE SET NULL,
                      horario_programado TIMESTAMP NOT NULL,
                      hora_confirmacao TIMESTAMP NULL,
                      status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
                      CONSTRAINT ck_dose_status CHECK (status IN ('PENDENTE', 'TOMADA', 'NAO_TOMADA', 'CONFIRMADA_COM_ATRASO'))
);

-- Impede doses duplicadas para o mesmo horário do mesmo tratamento (geração idempotente).
-- Também serve como índice de busca por (tratamento_id, horario_programado); um segundo índice com as mesmas
-- colunas seria redundante. Doses anonimizadas (tratamento_id NULL) não conflitam entre si.
CREATE UNIQUE INDEX uk_dose_tratamento_horario ON dose (tratamento_id, horario_programado);
