package com.medcare.senior.domain.exception;

/**
 * Token de dispositivo (FCM) vazio ou maior que o limite de 512 caracteres.
 */
public class TokenDispositivoInvalidoException extends RuntimeException {

    public TokenDispositivoInvalidoException() {
        super("O token do dispositivo é obrigatório e deve ter no máximo 512 caracteres");
    }
}
