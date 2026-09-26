package com.medcare.senior.domain.exception;

/**
 * Lançada quando o login falha. A mensagem é propositalmente genérica para não revelar
 * se o e-mail existe ou se a senha está incorreta.
 */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos");
    }
}
