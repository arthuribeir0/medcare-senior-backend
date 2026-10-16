-- Varredura de doses vencidas (a cada minuto): WHERE status = 'PENDENTE' AND horario_programado <= ?
-- ORDER BY horario_programado. Com status na frente, o índice entrega só as pendentes, já em ordem de horário.
CREATE INDEX idx_dose_status_horario ON dose (status, horario_programado);
