package com.medcare.senior.domain.tratamento;

/**
 * RN-007: o tratamento precisa ter ao menos um horário.
 */
public class HorariosObrigatoriosException extends RuntimeException {

    public HorariosObrigatoriosException() {
        super("Informe ao menos um horário para o tratamento");
    }
}
