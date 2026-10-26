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

        @Schema(description = "Código estável do erro (UPPER_SNAKE_CASE), para o app decidir o que fazer sem depender "
                + "da mensagem. Ver a tabela em CodigoErro.", example = "VALIDACAO",
                allowableValues = {"VALIDACAO", "CORPO_INVALIDO", "REQUISICAO_INVALIDA", "SENHA_FRACA",
                        "CONSENTIMENTO_OBRIGATORIO", "TOKEN_DISPOSITIVO_INVALIDO", "CODIGO_INVALIDO",
                        "DADOS_OBRIGATORIOS_MEDICAMENTO", "TRATAMENTO_INVALIDO", "CREDENCIAIS_INVALIDAS", "SESSAO_INVALIDA", "NAO_AUTENTICADO",
                        "ACESSO_NEGADO",
                        "RECURSO_NAO_ENCONTRADO", "METODO_NAO_PERMITIDO", "EMAIL_DUPLICADO", "PACIENTE_DUPLICADO",
                        "TRATAMENTO_ATIVO", "DOSE_JA_REGISTRADA", "DOSE_MUITO_CEDO", "DOSE_ENCERRADA",
                        "PERIODO_SEM_REGISTROS", "LIMITE_TENTATIVAS", "ERRO_INTERNO"})
        String codigo,

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
