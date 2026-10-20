package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Tolerância efetiva do cuidador: a própria ou, se ele não configurou, a padrão")
public record ToleranciaResponseDTO(
        @Schema(example = "30")
        int toleranciaMinutos
) {
}
