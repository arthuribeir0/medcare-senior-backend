package com.medcare.senior.domain.exception;

/**
 * RN-003: um cuidador não pode ter dois pacientes com o mesmo nome e a mesma data de nascimento.
 */
public class PacienteJaCadastradoException extends RuntimeException {

    public PacienteJaCadastradoException() {
        super("Já existe um paciente com este nome e data de nascimento vinculado a este cuidador");
    }
}
