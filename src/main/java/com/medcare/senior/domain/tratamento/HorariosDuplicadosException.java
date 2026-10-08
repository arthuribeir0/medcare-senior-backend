package com.medcare.senior.domain.tratamento;

import java.time.LocalTime;

/**
 * RN-007: o mesmo horário não pode aparecer duas vezes na lista de horários de um tratamento.
 */
public class HorariosDuplicadosException extends RuntimeException {

    public HorariosDuplicadosException(LocalTime horario) {
        super("O horário " + horario + " foi informado mais de uma vez no tratamento");
    }
}
