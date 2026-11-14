package com.medcare.senior.domain.email;

import java.time.LocalDateTime;

/**
 * Código de 6 dígitos enviado ao e-mail do cuidador, como fica gravado: só o hash (SHA-256), nunca o código.
 *
 * @param tentativas códigos errados já informados para este código
 */
public record CodigoPorEmail(Long cuidadorId, FinalidadeDoCodigo finalidade, String codigoHash, LocalDateTime criadoEm,
                             LocalDateTime expiraEm, int tentativas) {

    public boolean expirado(LocalDateTime agora) {
        return !agora.isBefore(expiraEm);
    }
}
