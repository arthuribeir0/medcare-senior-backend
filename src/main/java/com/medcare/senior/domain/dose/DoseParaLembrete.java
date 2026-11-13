package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;

/**
 * Dose PENDENTE cujo horário já passou, ainda antes do aviso ao cuidador e ainda sem lembrete do servidor, de um
 * paciente com aparelho registrado. Só existe para doses de tratamento e medicamento não removidos.
 *
 * @param doseId            a dose ainda PENDENTE
 * @param horarioProgramado horário da dose
 * @param pacienteId        destinatário do lembrete
 * @param nomeMedicamento   nome do remédio, para o texto do lembrete
 * @param toleranciaMinutos tolerância própria do cuidador (nula = a padrão), para saber até quando o lembrete vale
 */
public record DoseParaLembrete(Long doseId, LocalDateTime horarioProgramado, Long pacienteId, String nomeMedicamento,
                               Integer toleranciaMinutos) {
}
