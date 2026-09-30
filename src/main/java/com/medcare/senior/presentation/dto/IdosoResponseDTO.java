package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.Idoso;

import java.time.LocalDate;

public record IdosoResponseDTO(
        Long id,
        String nome,
        LocalDate dataNascimento,
        String telefone,
        Long cuidadorId
) {

    public static IdosoResponseDTO from(Idoso idoso) {
        return new IdosoResponseDTO(idoso.getId(), idoso.getNome(), idoso.getDataNascimento(),
                idoso.getTelefone(), idoso.getCuidadorId());
    }
}
