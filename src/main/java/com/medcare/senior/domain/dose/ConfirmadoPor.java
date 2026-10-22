package com.medcare.senior.domain.dose;

/**
 * Quem confirmou a dose (dose.confirmado_por). Gravado pelo nome: não renomeie.
 */
public enum ConfirmadoPor {

    /** O próprio idoso, no aparelho dele. */
    IDOSO,

    /** O cuidador responsável pelo idoso, na tela da dose. */
    CUIDADOR
}
