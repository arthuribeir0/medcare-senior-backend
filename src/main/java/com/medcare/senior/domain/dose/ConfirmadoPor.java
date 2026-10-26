package com.medcare.senior.domain.dose;

/**
 * Quem confirmou a dose (dose.confirmado_por). Gravado pelo nome: não renomeie.
 */
public enum ConfirmadoPor {

    /** O próprio paciente, no aparelho dele. */
    PACIENTE,

    /** O cuidador responsável pelo paciente, na tela da dose. */
    CUIDADOR
}
