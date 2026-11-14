/*
 * DTO de saída (corpo da resposta): contrato JSON de mensagens lidas. Campos: marcadas. A API devolve DTOs, nunca
 * as entidades.
 */
package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resultado de marcar a conversa como lida")
public record MensagensLidasResponseDTO(
        @Schema(description = "Quantas mensagens foram marcadas agora (0 se já estavam lidas)", example = "3")
        int marcadas
) {
}
