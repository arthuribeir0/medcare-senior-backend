package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.medicamento.Medicamento;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Medicamento cadastrado")
public record MedicamentoResponseDTO(
        @Schema(example = "100")
        Long id,

        @Schema(example = "Losartana")
        String nome,

        @Schema(example = "50mg")
        String dosagem,

        @Schema(example = "Comprimido")
        String tipo,

        @Schema(example = "Redondo")
        String formato,

        @Schema(example = "Branco")
        String cor,

        @Schema(example = "30")
        Integer quantidadeEstoque,

        @Schema(example = "https://consultas.anvisa.gov.br/bula/losartana.pdf")
        String bulaUrl,

        @Schema(example = "10")
        Long idosoId
) {

    public static MedicamentoResponseDTO from(Medicamento medicamento) {
        return new MedicamentoResponseDTO(medicamento.getId(), medicamento.getNome(), medicamento.getDosagem(),
                medicamento.getTipo(), medicamento.getFormato(), medicamento.getCor(),
                medicamento.getQuantidadeEstoque(), medicamento.getBulaUrl(), medicamento.getIdosoId());
    }
}
