package com.medcare.senior.domain.dose;

/**
 * RN-008: só uma dose PENDENTE pode ser registrada; dose já registrada é imutável.
 */
public class TransicaoDeDoseInvalidaException extends RuntimeException {

    public TransicaoDeDoseInvalidaException(Long doseId, StatusDose atual, StatusDose destino) {
        super("A dose " + doseId + " está " + atual + " e não pode passar para " + destino
                + ": somente doses PENDENTES podem ser registradas");
    }
}
