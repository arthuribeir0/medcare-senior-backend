package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.medicamento.Medicamento;

public record MedicamentoResponseDTO(
        Long id,
        String nome,
        String dosagem,
        String tipo,
        String formato,
        String cor,
        Integer quantidadeEstoque,
        String bulaUrl,
        Long idosoId
) {

    public static MedicamentoResponseDTO from(Medicamento medicamento) {
        return new MedicamentoResponseDTO(medicamento.getId(), medicamento.getNome(), medicamento.getDosagem(),
                medicamento.getTipo(), medicamento.getFormato(), medicamento.getCor(),
                medicamento.getQuantidadeEstoque(), medicamento.getBulaUrl(), medicamento.getIdosoId());
    }
}
