package com.medcare.senior.domain.tratamento;

/**
 * RN-007: o tratamento precisa ter ao menos um dia da semana.
 */
public class DiasSemanaObrigatoriosException extends RuntimeException {

    public DiasSemanaObrigatoriosException() {
        super("Informe ao menos um dia da semana para o tratamento");
    }
}
