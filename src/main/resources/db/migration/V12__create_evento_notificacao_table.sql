-- Evento de notificação (RN-009, RN-010): o aviso ao cuidador de que uma dose não foi confirmada dentro da tolerância.
-- Um evento por dose, no máximo: o UNIQUE em dose_id torna o aviso idempotente (INSERT ... ON CONFLICT DO NOTHING),
-- mesmo com duas execuções do job ao mesmo tempo. A dose continua PENDENTE depois do aviso.
--   status PENDENTE  = criado e ainda não entregue (inclui falha temporária, que a retentativa reprocessa)
--   status ENVIADO   = aceito pelo provedor de push
--   status FALHA     = desistência definitiva; o motivo fica em motivo_falha (ex.: TOKEN_INVALIDO, SEM_TOKEN)
-- tentativas e ultima_tentativa controlam a retentativa com intervalo crescente (sem coluna extra).
-- ON DELETE CASCADE: o evento não tem sentido sem a dose; a exclusão de conta (LGPD) também os apaga explicitamente.
CREATE TABLE evento_notificacao (
                                    id BIGSERIAL PRIMARY KEY,
                                    status VARCHAR(20) NOT NULL,
                                    tentativas INTEGER NOT NULL DEFAULT 0,
                                    ultima_tentativa TIMESTAMP NULL,
                                    dose_id BIGINT NOT NULL REFERENCES dose (id) ON DELETE CASCADE,
                                    motivo_falha VARCHAR(50) NULL,
                                    CONSTRAINT uk_evento_notificacao_dose UNIQUE (dose_id),
                                    CONSTRAINT ck_evento_notificacao_status CHECK (status IN ('PENDENTE', 'ENVIADO', 'FALHA')),
                                    CONSTRAINT ck_evento_notificacao_tentativas CHECK (tentativas >= 0)
);

-- Retentativa (a cada 5 minutos): WHERE status = 'PENDENTE' ... ORDER BY ultima_tentativa. Parcial: os eventos
-- ENVIADO e FALHA, que são quase todos, não entram no índice.
CREATE INDEX idx_evento_notificacao_pendente ON evento_notificacao (ultima_tentativa NULLS FIRST, id) WHERE status = 'PENDENTE';
