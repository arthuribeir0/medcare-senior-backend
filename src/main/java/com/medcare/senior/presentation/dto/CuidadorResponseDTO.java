package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.Cuidador;

public record CuidadorResponseDTO(
        Long id,
        String nome,
        String email
) {

    public static CuidadorResponseDTO from(Cuidador cuidador) {
        return new CuidadorResponseDTO(cuidador.getId(), cuidador.getNome(), cuidador.getEmail());
    }
}
