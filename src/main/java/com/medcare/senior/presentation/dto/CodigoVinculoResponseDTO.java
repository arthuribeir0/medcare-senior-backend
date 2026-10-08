package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.usuario.CodigoVinculo;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Código de vínculo gerado para parear o aparelho do idoso")
public record CodigoVinculoResponseDTO(
        @Schema(description = "Código de 6 dígitos, de uso único", example = "042917")
        String codigo,

        @Schema(description = "ID do idoso", example = "10")
        Long idosoId,

        @Schema(description = "Data e hora de expiração do código", example = "2026-10-09T14:30:00")
        LocalDateTime expiraEm
) {

    public static CodigoVinculoResponseDTO from(CodigoVinculo codigoVinculo) {
        return new CodigoVinculoResponseDTO(codigoVinculo.getCodigoUnico(), codigoVinculo.getIdosoId(),
                codigoVinculo.getExpiraEm());
    }
}
