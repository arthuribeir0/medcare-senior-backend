-- Mensagens entre o cuidador e o idoso (UC-010, HU12). A conversa é sempre de um idoso com o seu único cuidador, então
-- a mensagem guarda o idoso e quem a escreveu (CUIDADOR ou IDOSO); o destinatário é o outro lado da conversa.
--   conteudo  = texto de 1 a 500 caracteres (o app recusa mensagem vazia: UC-010, fluxo 3a). Dado pessoal: nunca em log.
--   criada_em = envio, no fuso da aplicação
--   lida_em   = quando o destinatário abriu a conversa; NULL = não lida
-- ON DELETE CASCADE: a mensagem não tem sentido sem o idoso; a exclusão de conta (LGPD) também as apaga explicitamente.
CREATE TABLE mensagem (
                          id BIGSERIAL PRIMARY KEY,
                          idoso_id BIGINT NOT NULL REFERENCES idoso (id) ON DELETE CASCADE,
                          remetente VARCHAR(10) NOT NULL,
                          conteudo TEXT NOT NULL,
                          criada_em TIMESTAMP NOT NULL,
                          lida_em TIMESTAMP NULL,
                          CONSTRAINT ck_mensagem_remetente CHECK (remetente IN ('CUIDADOR', 'IDOSO')),
                          CONSTRAINT ck_mensagem_conteudo CHECK (char_length(conteudo) BETWEEN 1 AND 500)
);

-- Conversa de um idoso, da mais recente para a mais antiga (o índice também é lido de trás para a frente)
CREATE INDEX idx_mensagem_idoso_criada_em ON mensagem (idoso_id, criada_em);
-- Contador de não lidas: parcial, só as ainda não lidas entram
CREATE INDEX idx_mensagem_nao_lidas ON mensagem (idoso_id, remetente) WHERE lida_em IS NULL;
