-- Tolerância do cuidador: o banco passa a aceitar de 1 a 120 minutos (o mínimo do modo de teste). O mínimo em vigor
-- (5 minutos, ou 1 com app.modo-teste ligado) é conferido pela aplicação.
ALTER TABLE configuracao_alerta DROP CONSTRAINT ck_configuracao_alerta_tolerancia;
ALTER TABLE configuracao_alerta
    ADD CONSTRAINT ck_configuracao_alerta_tolerancia CHECK (tolerancia_minutos BETWEEN 1 AND 120);
