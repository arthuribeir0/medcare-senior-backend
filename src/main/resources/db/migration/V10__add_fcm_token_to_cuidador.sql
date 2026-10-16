-- Token de dispositivo (Firebase Cloud Messaging) do aparelho do cuidador, para receber avisos push.
-- Dado pessoal (LGPD): some junto com a linha do cuidador na exclusão de conta. NULL = sem aparelho registrado.
ALTER TABLE cuidador
    ADD COLUMN fcm_token VARCHAR(512) NULL;
