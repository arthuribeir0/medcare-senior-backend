package com.medcare.senior.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ValidarCodigoRequestDTO(

        @NotBlank(message = "O código é obrigatório")
        String codigo
) {
}
