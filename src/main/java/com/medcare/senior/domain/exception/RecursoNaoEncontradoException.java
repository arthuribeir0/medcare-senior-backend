/*
 * Exceção de domínio para recurso inexistente ou de outro dono. Vira resposta 404, igual nos dois casos, para não
 * revelar se o ID existe.
 */
package com.medcare.senior.domain.exception;

public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
