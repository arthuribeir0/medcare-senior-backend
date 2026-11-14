-- Confirmação do e-mail do cuidador e redefinição de senha por código enviado ao e-mail.
--
-- email_verificado: o cadastro pela API grava false e envia o código; o padrão do banco é true para as contas que já
-- existem (e as criadas fora da API, como a demonstração) continuarem funcionando sem confirmar.
ALTER TABLE cuidador ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT TRUE;

-- Um código vivo por cuidador e finalidade (um novo substitui o anterior). Só o hash SHA-256 do código é guardado.
CREATE TABLE codigo_email (
    id          BIGSERIAL PRIMARY KEY,
    cuidador_id BIGINT      NOT NULL REFERENCES cuidador (id) ON DELETE CASCADE,
    finalidade  VARCHAR(20) NOT NULL,
    codigo_hash VARCHAR(64) NOT NULL,
    criado_em   TIMESTAMP   NOT NULL,
    expira_em   TIMESTAMP   NOT NULL,
    tentativas  INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT uk_codigo_email_cuidador_finalidade UNIQUE (cuidador_id, finalidade),
    CONSTRAINT ck_codigo_email_finalidade CHECK (finalidade IN ('VERIFICACAO', 'REDEFINICAO_SENHA')),
    CONSTRAINT ck_codigo_email_tentativas CHECK (tentativas >= 0),
    CONSTRAINT ck_codigo_email_validade CHECK (expira_em > criado_em)
);
