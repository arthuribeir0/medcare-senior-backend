package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

@Schema(description = "Dados para cadastro de paciente")
public record PacienteRequestDTO(

        @Schema(description = "Nome completo", example = "José Souza")
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @Schema(description = "Data de nascimento (no passado)", example = "1945-03-12")
        @NotNull(message = "A data de nascimento é obrigatória")
        @Past(message = "A data de nascimento deve estar no passado")
        LocalDate dataNascimento,

        @Schema(description = "Telefone de contato", example = "11988887777")
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone
) {
}
