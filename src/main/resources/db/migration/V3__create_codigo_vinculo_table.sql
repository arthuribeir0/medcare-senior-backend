CREATE TABLE codigo_vinculo (
                                id BIGSERIAL PRIMARY KEY,
                                codigo_unico VARCHAR(6) NOT NULL,
                                cuidador_id BIGINT NOT NULL REFERENCES cuidador (id),
                                idoso_id BIGINT NOT NULL REFERENCES idoso (id),
                                expira_em TIMESTAMP NOT NULL,
                                usado BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_codigo_vinculo_codigo_unico ON codigo_vinculo (codigo_unico);
CREATE INDEX idx_codigo_vinculo_idoso_id ON codigo_vinculo (idoso_id);
