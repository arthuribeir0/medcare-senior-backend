package com.medcare.senior.presentation.dto;

import com.medcare.senior.application.usecase.BuscarIdosoAutenticadoUseCase;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Dados do idoso autenticado")
public record IdosoAutenticadoResponseDTO(
        @Schema(example = "10")
        Long id,

        @Schema(example = "José Souza")
        String nome,

        @Schema(description = "Nome de quem cuida do idoso", example = "Ana Souza")
        String cuidadorNome
) {

    public static IdosoAutenticadoResponseDTO from(BuscarIdosoAutenticadoUseCase.Resultado resultado) {
        return new IdosoAutenticadoResponseDTO(resultado.idoso().getId(), resultado.idoso().getNome(),
                resultado.cuidadorNome());
    }
}
