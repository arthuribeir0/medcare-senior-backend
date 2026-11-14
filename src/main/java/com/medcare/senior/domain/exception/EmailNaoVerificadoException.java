package com.medcare.senior.domain.exception;

/** Operação que exige o e-mail do cuidador confirmado (cadastrar paciente). */
public class EmailNaoVerificadoException extends RuntimeException {

    public EmailNaoVerificadoException() {
        super("Confirme o seu e-mail com o código que enviamos antes de cadastrar pacientes.");
    }
}
