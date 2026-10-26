package com.medcare.senior.infrastructure.persistence.projection;

/**
 * Linha devolvida pelo encerramento atômico das doses vencidas (RETURNING): a dose encerrada e o paciente dela.
 */
public interface DoseEncerradaView {

    Long getDoseId();

    Long getPacienteId();
}
