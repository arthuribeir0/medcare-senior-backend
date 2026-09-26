package com.medcare.senior.presentation.dto;

public record AuthResponseDTO(
        String token,
        String tipo
) {

    public static AuthResponseDTO bearer(String token) {
        return new AuthResponseDTO(token, "Bearer");
    }
}
