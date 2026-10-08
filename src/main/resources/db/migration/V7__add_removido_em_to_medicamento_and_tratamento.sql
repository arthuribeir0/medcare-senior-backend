-- Remoção lógica (RN-008: o histórico de doses precisa sobreviver à remoção do medicamento).
-- removido_em NULL = item ativo; preenchido = removido pelo cuidador e invisível nas consultas da API.
-- A exclusão de conta (LGPD) continua apagando fisicamente, inclusive itens já removidos logicamente.
ALTER TABLE medicamento ADD COLUMN removido_em TIMESTAMP NULL;
ALTER TABLE tratamento ADD COLUMN removido_em TIMESTAMP NULL;
