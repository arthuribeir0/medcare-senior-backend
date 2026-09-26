package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.CodigoVinculo;

import java.time.LocalDateTime;

public record CodigoVinculoResponseDTO(
        String codigo,
        Long idosoId,
        LocalDateTime expiraEm
) {

    public static CodigoVinculoResponseDTO from(CodigoVinculo codigoVinculo) {
        return new CodigoVinculoResponseDTO(codigoVinculo.getCodigoUnico(), codigoVinculo.getIdosoId(),
                codigoVinculo.getExpiraEm());
    }
}
