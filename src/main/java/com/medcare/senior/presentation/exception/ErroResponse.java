package com.medcare.senior.presentation.exception;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Corpo padrão das respostas de erro")
public record ErroResponse(
        @Schema(example = "2026-10-08T14:30:00")
        LocalDateTime timestamp,

        @Schema(example = "400")
        int status,

        @Schema(example = "Bad Request")
        String erro,

        @Schema(example = "Um ou mais campos são inválidos")
        String mensagem,

        @Schema(description = "Campos inválidos (vazio quando o erro não é de validação)")
        List<CampoInvalido> campos
) {

    @Schema(description = "Campo que não passou na validação")
    public record CampoInvalido(
            @Schema(example = "email")
            String campo,

            @Schema(example = "O e-mail informado é inválido")
            String mensagem
    ) {
    }
}
