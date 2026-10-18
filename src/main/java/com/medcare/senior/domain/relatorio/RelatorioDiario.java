package com.medcare.senior.domain.relatorio;

import com.medcare.senior.domain.dose.DoseAvaliada;

import java.time.LocalDate;
import java.util.List;

/**
 * Relatório diário de uso dos medicamentos de um idoso (UC-008 / HU06): as doses do dia, avaliadas no servidor, e os
 * totais.
 *
 * @param doses ordenadas por horário
 */
public record RelatorioDiario(Long idosoId, String idosoNome, LocalDate data, TotaisDoDia totais,
                              List<DoseAvaliada> doses) {
}
