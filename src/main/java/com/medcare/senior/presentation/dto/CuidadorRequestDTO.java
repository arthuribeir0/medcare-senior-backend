package com.medcare.senior.presentation.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CuidadorRequestDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail informado é inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,

        @NotBlank(message = "A senha é obrigatória")
        String senha,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone
) {
}
