package com.medcare.senior.domain.dose;

/**
 * Dose que a varredura acabou de encerrar como NAO_TOMADA.
 *
 * @param doseId  a dose encerrada
 * @param idosoId o idoso dela (para descartar o relatório diário em cache desse idoso)
 */
public record DoseEncerrada(Long doseId, Long idosoId) {
}
