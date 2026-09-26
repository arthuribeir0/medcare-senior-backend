package com.medcare.senior.domain.exception;

/**
 * RN-003: um cuidador não pode ter dois idosos com o mesmo nome e a mesma data de nascimento.
 */
public class IdosoJaCadastradoException extends RuntimeException {

    public IdosoJaCadastradoException() {
        super("Já existe um idoso com este nome e data de nascimento vinculado a este cuidador");
    }
}
