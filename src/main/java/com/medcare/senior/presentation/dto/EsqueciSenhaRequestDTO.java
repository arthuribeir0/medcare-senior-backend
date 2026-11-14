package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Pedido de código para criar uma senha nova")
public record EsqueciSenhaRequestDTO(

        @Schema(description = "E-mail da conta", example = "ana@email.com")
        @NotBlank(message = "O e-mail é obrigatório")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email
) {
}
