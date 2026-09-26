package com.medcare.senior.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record IdosoRequestDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone
) {
}
