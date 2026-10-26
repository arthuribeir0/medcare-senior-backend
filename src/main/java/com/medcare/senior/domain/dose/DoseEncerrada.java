package com.medcare.senior.domain.dose;

/**
 * Dose que a varredura acabou de encerrar como NAO_TOMADA.
 *
 * @param doseId  a dose encerrada
 * @param pacienteId o paciente dela (para descartar o relatório diário em cache desse paciente)
 */
public record DoseEncerrada(Long doseId, Long pacienteId) {
}
