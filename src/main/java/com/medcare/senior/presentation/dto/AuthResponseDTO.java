package com.medcare.senior.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.medcare.senior.domain.autenticacao.SessaoAutenticada;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Token de acesso emitido no login ou no pareamento")
public record AuthResponseDTO(
        @Schema(description = "JWT a enviar no header Authorization", example = "eyJhbGciOiJIUzI1NiJ9...")
        String token,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tipo,

        @Schema(description = "ID do cuidador (login) ou do paciente (pareamento)", example = "1")
        Long id,

        @Schema(description = "Nome do cuidador (login) ou do paciente (pareamento)", example = "Ana Souza")
        String nome,

        @Schema(description = "Só no login do cuidador: o e-mail já foi confirmado. Sem isso, o app pede o código "
                + "enviado ao e-mail antes de deixar cadastrar pacientes. Ausente no pareamento.", example = "true",
                nullable = true)
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean emailVerificado
) {

    public static AuthResponseDTO bearer(SessaoAutenticada sessao) {
        return new AuthResponseDTO(sessao.token(), "Bearer", sessao.usuarioId(), sessao.nome(), sessao.emailVerificado());
    }
}
