CREATE TABLE idoso (
                       id BIGSERIAL PRIMARY KEY,
                       nome VARCHAR(255) NOT NULL,
                       data_nascimento DATE NOT NULL,
                       telefone VARCHAR(20),
                       cuidador_id BIGINT NOT NULL REFERENCES cuidador (id)
);

CREATE INDEX idx_idoso_cuidador_id ON idoso (cuidador_id);

-- RN-003: mesmo cuidador não pode ter dois idosos com mesmo nome (sem diferenciar maiúsculas) e mesma data de nascimento
CREATE UNIQUE INDEX uk_idoso_cuidador_nome_nascimento ON idoso (cuidador_id, LOWER(nome), data_nascimento);
