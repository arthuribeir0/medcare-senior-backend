package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.configuracao.Tolerancia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Tolerância do cuidador (RN-009): minutos depois do horário da dose até o aviso")
public record ToleranciaRequestDTO(

        @Schema(description = "Minutos, de 5 a 120 (inclusive)", example = "30", minimum = "5", maximum = "120",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A tolerância é obrigatória")
        @Min(value = Tolerancia.MINIMO_MINUTOS, message = "A tolerância deve ficar entre 5 e 120 minutos")
        @Max(value = Tolerancia.MAXIMO_MINUTOS, message = "A tolerância deve ficar entre 5 e 120 minutos")
        Integer toleranciaMinutos
) {
}
