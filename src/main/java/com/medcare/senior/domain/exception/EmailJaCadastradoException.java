package com.medcare.senior.domain.exception;

/**
 * RN-001: o e-mail de cuidador deve ser único no sistema.
 */
public class EmailJaCadastradoException extends RuntimeException {

    public EmailJaCadastradoException(String email) {
        super("Já existe um cuidador cadastrado com o e-mail: " + email);
    }
}
