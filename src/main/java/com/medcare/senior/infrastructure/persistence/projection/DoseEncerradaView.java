package com.medcare.senior.infrastructure.persistence.projection;

/**
 * Linha devolvida pelo encerramento atômico das doses vencidas (RETURNING): a dose encerrada e o idoso dela.
 */
public interface DoseEncerradaView {

    Long getDoseId();

    Long getIdosoId();
}
