-- RN-005: nome, dosagem e tipo obrigatórios
CREATE TABLE medicamento (
                             id BIGSERIAL PRIMARY KEY,
                             nome VARCHAR(255) NOT NULL,
                             dosagem VARCHAR(100) NOT NULL,
                             tipo VARCHAR(100) NOT NULL,
                             formato VARCHAR(100),
                             cor VARCHAR(50),
                             quantidade_estoque INTEGER CHECK (quantidade_estoque >= 0),
                             bula_url VARCHAR(2048),
                             idoso_id BIGINT NOT NULL REFERENCES idoso (id)
);

CREATE INDEX idx_medicamento_idoso_id ON medicamento (idoso_id);
