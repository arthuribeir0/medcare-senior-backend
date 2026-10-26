package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Código de vínculo informado no aparelho do paciente")
public record ValidarCodigoRequestDTO(

        @Schema(description = "Código de 6 dígitos gerado pelo cuidador", example = "042917")
        @NotBlank(message = "O código é obrigatório")
        String codigo
) {
}
