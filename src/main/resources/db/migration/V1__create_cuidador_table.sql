CREATE TABLE cuidador (
                          id BIGSERIAL PRIMARY KEY,
                          nome VARCHAR(255) NOT NULL,
                          email VARCHAR(255) NOT NULL UNIQUE,
                          senha_hash VARCHAR(255) NOT NULL,
                          telefone VARCHAR(20),
                          data_cadastro TIMESTAMP NOT NULL
);