package com.medcare.senior.presentation.dto;

import com.medcare.senior.application.usecase.BuscarPacienteAutenticadoUseCase;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados do paciente autenticado")
public record PacienteAutenticadoResponseDTO(
        @Schema(example = "10")
        Long id,

        @Schema(example = "José Souza")
        String nome,

        @Schema(description = "Nome de quem cuida do paciente", example = "Ana Souza")
        String cuidadorNome
) {

    public static PacienteAutenticadoResponseDTO from(BuscarPacienteAutenticadoUseCase.Resultado resultado) {
        return new PacienteAutenticadoResponseDTO(resultado.paciente().getId(), resultado.paciente().getNome(),
                resultado.cuidadorNome());
    }
}
