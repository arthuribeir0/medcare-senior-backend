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
        String email,

        @Schema(description = "Telefone de contato (pode ser null)", example = "11999999999", nullable = true)
        String telefone
) {

    public static CuidadorResponseDTO from(Cuidador cuidador) {
        return new CuidadorResponseDTO(cuidador.getId(), cuidador.getNome(), cuidador.getEmail(), cuidador.getTelefone());
    }
}
