package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.Paciente;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Paciente cadastrado")
public record PacienteResponseDTO(
        @Schema(example = "10")
        Long id,

        @Schema(example = "José Souza")
        String nome,

        @Schema(example = "1945-03-12")
        LocalDate dataNascimento,

        @Schema(example = "11988887777")
        String telefone,

        @Schema(description = "ID do cuidador responsável", example = "1")
        Long cuidadorId
) {

    public static PacienteResponseDTO from(Paciente paciente) {
        return new PacienteResponseDTO(paciente.getId(), paciente.getNome(), paciente.getDataNascimento(),
                paciente.getTelefone(), paciente.getCuidadorId());
    }
}
