-- O app foi pensado para idosos, mas quem é acompanhado não precisa ser idoso: o papel passa a se chamar paciente.
-- As migrações anteriores ficam com os nomes antigos (o Flyway confere o checksum de cada uma); aqui a tabela, as
-- colunas, a sequência, os índices, as restrições e os valores já gravados ganham o nome novo.

ALTER TABLE idoso RENAME TO paciente;
ALTER SEQUENCE idoso_id_seq RENAME TO paciente_id_seq;
ALTER TABLE paciente RENAME CONSTRAINT idoso_pkey TO paciente_pkey;
ALTER TABLE paciente RENAME CONSTRAINT idoso_cuidador_id_fkey TO paciente_cuidador_id_fkey;
ALTER INDEX idx_idoso_cuidador_id RENAME TO idx_paciente_cuidador_id;
ALTER INDEX uk_idoso_cuidador_nome_nascimento RENAME TO uk_paciente_cuidador_nome_nascimento;

ALTER TABLE codigo_vinculo RENAME COLUMN idoso_id TO paciente_id;
ALTER TABLE codigo_vinculo RENAME CONSTRAINT codigo_vinculo_idoso_id_fkey TO codigo_vinculo_paciente_id_fkey;
ALTER INDEX idx_codigo_vinculo_idoso_id RENAME TO idx_codigo_vinculo_paciente_id;

ALTER TABLE medicamento RENAME COLUMN idoso_id TO paciente_id;
ALTER TABLE medicamento RENAME CONSTRAINT medicamento_idoso_id_fkey TO medicamento_paciente_id_fkey;
ALTER INDEX idx_medicamento_idoso_id RENAME TO idx_medicamento_paciente_id;

ALTER TABLE mensagem RENAME COLUMN idoso_id TO paciente_id;
ALTER TABLE mensagem RENAME CONSTRAINT mensagem_idoso_id_fkey TO mensagem_paciente_id_fkey;
ALTER INDEX idx_mensagem_idoso_criada_em RENAME TO idx_mensagem_paciente_criada_em;

-- Valores gravados: o CHECK antigo não aceita o valor novo, então sai antes da conversão e volta depois
ALTER TABLE mensagem DROP CONSTRAINT ck_mensagem_remetente;
UPDATE mensagem SET remetente = 'PACIENTE' WHERE remetente = 'IDOSO';
ALTER TABLE mensagem ADD CONSTRAINT ck_mensagem_remetente CHECK (remetente IN ('CUIDADOR', 'PACIENTE'));

ALTER TABLE dose DROP CONSTRAINT ck_dose_confirmado_por;
UPDATE dose SET confirmado_por = 'PACIENTE' WHERE confirmado_por = 'IDOSO';
ALTER TABLE dose ADD CONSTRAINT ck_dose_confirmado_por CHECK (confirmado_por IN ('PACIENTE', 'CUIDADOR'));
