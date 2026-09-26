package com.medcare.senior.domain.exception;

/**
 * O usuário autenticado não tem o papel necessário para a operação.
 */
public class AcessoNegadoException extends RuntimeException {

    public AcessoNegadoException() {
        super("Acesso negado");
    }
}
