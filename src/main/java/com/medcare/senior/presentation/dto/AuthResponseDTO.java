package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.autenticacao.SessaoAutenticada;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acesso emitido no login ou no pareamento")
public record AuthResponseDTO(
        @Schema(description = "JWT a enviar no header Authorization", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tipo,

        @Schema(description = "ID do cuidador (login) ou do idoso (pareamento)", example = "1")
        Long id,

        @Schema(description = "Nome do cuidador (login) ou do idoso (pareamento)", example = "Ana Souza")
        String nome
) {

    public static AuthResponseDTO bearer(SessaoAutenticada sessao) {
        return new AuthResponseDTO(sessao.token(), "Bearer", sessao.usuarioId(), sessao.nome());
    }
}
