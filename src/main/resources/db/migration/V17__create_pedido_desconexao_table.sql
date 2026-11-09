-- Pedido do paciente para sair da conta no aparelho: só o cuidador vinculado autoriza ou recusa. Expira se ninguém
-- decidir a tempo (app.desconexao.validade-horas). decidido_em marca quando o pedido deixou de estar PENDENTE
-- (decisão, cancelamento ou expiração); decidido_por_cuidador_id só existe em AUTORIZADO e RECUSADO.
CREATE TABLE pedido_desconexao (
                                   id BIGSERIAL PRIMARY KEY,
                                   paciente_id BIGINT NOT NULL REFERENCES paciente (id) ON DELETE CASCADE,
                                   status VARCHAR(20) NOT NULL,
                                   criado_em TIMESTAMP NOT NULL,
                                   expira_em TIMESTAMP NOT NULL,
                                   decidido_em TIMESTAMP NULL,
                                   decidido_por_cuidador_id BIGINT NULL REFERENCES cuidador (id) ON DELETE SET NULL,
                                   CONSTRAINT ck_pedido_desconexao_status
                                       CHECK (status IN ('PENDENTE', 'AUTORIZADO', 'RECUSADO', 'EXPIRADO', 'CANCELADO')),
                                   CONSTRAINT ck_pedido_desconexao_validade CHECK (expira_em > criado_em),
                                   CONSTRAINT ck_pedido_desconexao_decidido_em CHECK ((decidido_em IS NULL) = (status = 'PENDENTE')),
                                   CONSTRAINT ck_pedido_desconexao_decidido_por
                                       CHECK (decidido_por_cuidador_id IS NULL OR status IN ('AUTORIZADO', 'RECUSADO'))
);

-- No máximo um pedido PENDENTE por paciente (a última garantia contra dois pedidos simultâneos)
CREATE UNIQUE INDEX uk_pedido_desconexao_pendente ON pedido_desconexao (paciente_id) WHERE status = 'PENDENTE';
-- Pedido mais recente do paciente
CREATE INDEX idx_pedido_desconexao_paciente ON pedido_desconexao (paciente_id, id DESC);
-- Job de expiração: só os pendentes, pelo prazo
CREATE INDEX idx_pedido_desconexao_pendente_expira ON pedido_desconexao (expira_em) WHERE status = 'PENDENTE';

-- Tokens do paciente emitidos ANTES deste momento deixam de valer (desconexão autorizada ou feita pelo cuidador).
-- Com fuso, para comparar direto com o "emitido em" do JWT. NULL = nenhuma desconexão.
ALTER TABLE paciente ADD COLUMN sessao_valida_desde TIMESTAMPTZ NULL;
