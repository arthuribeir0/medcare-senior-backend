package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;

/**
 * Dose PENDENTE que passou do fim da tolerância, ainda não encerrada e ainda sem aviso, com os dados de quem deve ser
 * avisado. Só existe para doses de tratamento e medicamento não removidos (doses anonimizadas não têm dono).
 *
 * @param doseId            a dose ainda PENDENTE
 * @param horarioProgramado horário da dose
 * @param cuidadorId        cuidador responsável pelo idoso (destinatário do aviso)
 * @param idosoId           idoso que deveria ter tomado a dose
 * @param nomeIdoso         nome do idoso, para o texto do aviso
 */
public record DoseParaAviso(Long doseId, LocalDateTime horarioProgramado, Long cuidadorId, Long idosoId, String nomeIdoso) {
}
