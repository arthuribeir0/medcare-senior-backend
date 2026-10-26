package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * As regras do conteúdo (1 a 500 caracteres, sem só espaços) ficam no domínio (Mensagem.nova) e respondem 400
 * VALIDACAO no campo "conteudo".
 */
@Schema(description = "Mensagem a enviar (UC-010)")
public record MensagemRequestDTO(
        @Schema(description = "Paciente da conversa: obrigatório para o cuidador; o paciente escreve sempre na própria "
                + "conversa (pode omitir; outro paciente responde 403)", example = "10", nullable = true)
        Long pacienteId,

        @Schema(description = "Texto de 1 a 500 caracteres; espaços nas pontas são retirados",
                example = "Bom dia! Lembre do remédio das 8h.", maxLength = 500)
        String conteudo
) {
}
