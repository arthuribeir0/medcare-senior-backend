-- LGPD: registro do consentimento do cuidador no cadastro.
-- Cuidadores já existentes ficam com consentimento_lgpd = FALSE e sem data (não aceitaram o termo atual).
ALTER TABLE cuidador
    ADD COLUMN consentimento_lgpd BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN data_consentimento TIMESTAMP NULL;
