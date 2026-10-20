package com.medcare.senior.domain.relatorio;

import com.medcare.senior.domain.dose.StatusDose;

/**
 * Quantas doses de um medicamento têm um status gravado no período (uma linha do agrupamento feito no banco).
 */
public record ContagemDeDoses(Long medicamentoId, String nome, String dosagem, StatusDose status, long quantidade) {
}
