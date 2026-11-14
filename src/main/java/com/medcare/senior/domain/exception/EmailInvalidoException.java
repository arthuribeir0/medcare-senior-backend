package com.medcare.senior.domain.exception;

/** E-mail com formato errado ou de um domínio que não existe (ex.: "ana@gmial.com"). */
public class EmailInvalidoException extends RuntimeException {

    public EmailInvalidoException(String mensagem) {
        super(mensagem);
    }
}
