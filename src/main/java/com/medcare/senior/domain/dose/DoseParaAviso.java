package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;

/**
 * Dose PENDENTE que passou do fim da tolerância, ainda não encerrada e ainda sem aviso, com os dados de quem deve ser
 * avisado. Só existe para doses de tratamento e medicamento não removidos (doses anonimizadas não têm dono).
 *
 * @param doseId            a dose ainda PENDENTE
 * @param horarioProgramado horário da dose
 * @param cuidadorId        cuidador responsável pelo paciente (destinatário do aviso)
 * @param pacienteId           paciente que deveria ter tomado a dose
 * @param nomePaciente         nome do paciente, para o texto do aviso
 */
public record DoseParaAviso(Long doseId, LocalDateTime horarioProgramado, Long cuidadorId, Long pacienteId, String nomePaciente) {
}
