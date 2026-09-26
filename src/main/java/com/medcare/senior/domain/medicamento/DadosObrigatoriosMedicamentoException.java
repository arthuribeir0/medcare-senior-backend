package com.medcare.senior.domain.medicamento;

/**
 * RN-005: nome, dosagem e tipo do medicamento são obrigatórios.
 */
public class DadosObrigatoriosMedicamentoException extends RuntimeException {

    public DadosObrigatoriosMedicamentoException() {
        super("Nome, dosagem e tipo do medicamento são obrigatórios");
    }
}
