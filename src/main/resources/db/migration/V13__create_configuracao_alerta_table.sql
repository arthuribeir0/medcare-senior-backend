-- Configuração de alerta por cuidador (RN-009): a tolerância, em minutos depois do horário da dose, ao fim da qual o
-- cuidador é avisado de que a dose não foi confirmada. Sem linha = tolerância padrão (app.alertas.tolerancia-minutos).
-- No máximo 120 min, sempre menor que o encerramento da dose (180 min), que é fixo.
-- ON DELETE CASCADE: a exclusão da conta do cuidador (LGPD) apaga a configuração junto.
CREATE TABLE configuracao_alerta (
                                     cuidador_id BIGINT PRIMARY KEY REFERENCES cuidador (id) ON DELETE CASCADE,
                                     tolerancia_minutos INTEGER NOT NULL,
                                     CONSTRAINT ck_configuracao_alerta_tolerancia CHECK (tolerancia_minutos BETWEEN 5 AND 120)
);
