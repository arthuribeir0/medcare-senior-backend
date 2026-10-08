package com.medcare.senior.domain.tratamento;

/**
 * RN-007: a data de término do tratamento não pode ser anterior à data de início.
 */
public class PeriodoTratamentoInvalidoException extends RuntimeException {

    public PeriodoTratamentoInvalidoException() {
        super("A data de término do tratamento não pode ser anterior à data de início");
    }
}
