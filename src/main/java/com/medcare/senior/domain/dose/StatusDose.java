package com.medcare.senior.domain.dose;

public enum StatusDose {

    /**
     * Gerada pelo motor de doses, ainda sem registro. É a única situação em que a dose pode ser apagada
     * (regeneração ao editar o tratamento, remoção do medicamento, exclusão de conta).
     */
    PENDENTE,

    TOMADA,
    NAO_TOMADA,
    CONFIRMADA_COM_ATRASO;

    /**
     * Dose registrada faz parte do histórico de adesão e é imutável (RN-008).
     */
    public boolean isRegistrada() {
        return this != PENDENTE;
    }
}
