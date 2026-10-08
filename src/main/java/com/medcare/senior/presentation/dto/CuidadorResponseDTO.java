package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.Cuidador;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Cuidador cadastrado")
public record CuidadorResponseDTO(
        @Schema(example = "1")
        Long id,

        @Schema(example = "Ana Souza")
        String nome,

        @Schema(example = "ana@email.com")
        String email
) {

    public static CuidadorResponseDTO from(Cuidador cuidador) {
        return new CuidadorResponseDTO(cuidador.getId(), cuidador.getNome(), cuidador.getEmail());
    }
}
