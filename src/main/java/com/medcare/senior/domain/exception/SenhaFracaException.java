package com.medcare.senior.domain.exception;

/**
 * RN-002: a senha deve ter no mínimo 8 caracteres, com ao menos uma letra maiúscula,
 * uma minúscula e um número.
 */
public class SenhaFracaException extends RuntimeException {

    public SenhaFracaException() {
        super("A senha deve ter no mínimo 8 caracteres, com ao menos uma letra maiúscula, uma minúscula e um número");
    }
}
