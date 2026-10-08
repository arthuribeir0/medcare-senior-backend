package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credenciais do cuidador")
public record LoginRequestDTO(

        @Schema(example = "ana@email.com")
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail informado é inválido")
        String email,

        @Schema(example = "SenhaForte1")
        @NotBlank(message = "A senha é obrigatória")
        String senha
) {
}
