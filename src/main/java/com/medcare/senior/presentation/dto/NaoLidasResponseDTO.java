package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Total de mensagens recebidas e ainda não lidas por quem chama")
public record NaoLidasResponseDTO(
        @Schema(description = "Cuidador: somando as conversas de todos os seus pacientes; paciente: as do cuidador",
                example = "4")
        long naoLidas
) {
}
