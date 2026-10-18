package com.medcare.senior.domain.dose;

/**
 * Se a dose pode ser confirmada agora, e por que não.
 */
public enum Confirmabilidade {

    /** Dentro da janela [abreEm, encerraEm] e ainda PENDENTE. */
    OK,

    /** Já tem um status registrado (RN-008: imutável). */
    JA_REGISTRADA,

    /** Antes de abreEm. */
    MUITO_CEDO,

    /** Depois de encerraEm. */
    ENCERRADA
}
