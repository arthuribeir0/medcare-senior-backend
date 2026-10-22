-- O status da dose confirmada dentro da tolerância passa a se chamar CONFIRMADA_NO_HORARIO ("Confirmada no horário"
-- no app e no PDF). NAO_TOMADA continua com o mesmo nome.
-- O CHECK antigo não aceita o valor novo: sai antes da conversão das linhas e volta depois, com os valores novos.
ALTER TABLE dose DROP CONSTRAINT ck_dose_status;

UPDATE dose SET status = 'CONFIRMADA_NO_HORARIO' WHERE status = 'TOMADA';

ALTER TABLE dose ADD CONSTRAINT ck_dose_status
    CHECK (status IN ('PENDENTE', 'CONFIRMADA_NO_HORARIO', 'NAO_TOMADA', 'CONFIRMADA_COM_ATRASO'));
