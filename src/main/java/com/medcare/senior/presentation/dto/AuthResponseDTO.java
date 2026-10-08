package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acesso emitido no login ou no pareamento")
public record AuthResponseDTO(
        @Schema(description = "JWT a enviar no header Authorization", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tipo
) {

    public static AuthResponseDTO bearer(String token) {
        return new AuthResponseDTO(token, "Bearer");
    }
}
