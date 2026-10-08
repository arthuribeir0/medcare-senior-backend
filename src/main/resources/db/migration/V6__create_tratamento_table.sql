-- Tratamento: período e horários em que um medicamento deve ser tomado.
-- horarios: lista JSON de horários "HH:mm" (ex.: ["08:00", "20:00"])
-- dias_semana: lista JSON de dias em inglês, como em java.time.DayOfWeek (ex.: ["MONDAY", "THURSDAY"])
-- data_fim NULL = tratamento contínuo (sem data de término)
CREATE TABLE tratamento (
                            id BIGSERIAL PRIMARY KEY,
                            medicamento_id BIGINT NOT NULL REFERENCES medicamento (id),
                            data_inicio DATE NOT NULL,
                            data_fim DATE NULL,
                            horarios JSONB NOT NULL,
                            dias_semana JSONB NOT NULL
);

CREATE INDEX idx_tratamento_medicamento_id ON tratamento (medicamento_id);
