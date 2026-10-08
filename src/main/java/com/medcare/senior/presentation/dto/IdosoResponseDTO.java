package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.Idoso;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "Idoso cadastrado")
public record IdosoResponseDTO(
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

    public static IdosoResponseDTO from(Idoso idoso) {
        return new IdosoResponseDTO(idoso.getId(), idoso.getNome(), idoso.getDataNascimento(),
                idoso.getTelefone(), idoso.getCuidadorId());
    }
}
