-- O status da dose confirmada dentro da tolerância passa a se chamar CONFIRMADA_NO_HORARIO ("Confirmada no horário"
-- no app e no PDF). NAO_TOMADA continua com o mesmo nome.
-- O CHECK antigo não aceita o valor novo: sai antes da conversão das linhas e volta depois, com os valores novos.
ALTER TABLE dose DROP CONSTRAINT ck_dose_status;

UPDATE dose SET status = 'CONFIRMADA_NO_HORARIO' WHERE status = 'TOMADA';

ALTER TABLE dose ADD CONSTRAINT ck_dose_status
    CHECK (status IN ('PENDENTE', 'CONFIRMADA_NO_HORARIO', 'NAO_TOMADA', 'CONFIRMADA_COM_ATRASO'));

-- Quem confirmou a dose: o idoso, no aparelho dele, ou o cuidador, na tela da dose. Só as doses confirmadas (no
-- horário ou com atraso) têm o campo; PENDENTE e NAO_TOMADA ficam com NULL. Até aqui só o idoso confirmava, então as
-- doses já confirmadas recebem 'IDOSO'.
ALTER TABLE dose ADD COLUMN confirmado_por VARCHAR(10) NULL;

UPDATE dose SET confirmado_por = 'IDOSO' WHERE status IN ('CONFIRMADA_NO_HORARIO', 'CONFIRMADA_COM_ATRASO');

ALTER TABLE dose ADD CONSTRAINT ck_dose_confirmado_por CHECK (confirmado_por IN ('IDOSO', 'CUIDADOR'));

-- Preenchido se, e somente se, a dose foi confirmada
ALTER TABLE dose ADD CONSTRAINT ck_dose_confirmado_por_so_confirmada
    CHECK ((confirmado_por IS NOT NULL) = (status IN ('CONFIRMADA_NO_HORARIO', 'CONFIRMADA_COM_ATRASO')));
