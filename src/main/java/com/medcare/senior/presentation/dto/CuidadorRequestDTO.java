package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para cadastro de cuidador")
public record CuidadorRequestDTO(

        @Schema(description = "Nome completo", example = "Ana Souza")
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @Schema(description = "E-mail (único no sistema)", example = "ana@email.com")
        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "O e-mail informado é inválido")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,

        @Schema(description = "Mínimo de 8 caracteres, com maiúscula, minúscula e número", example = "SenhaForte1")
        @NotBlank(message = "A senha é obrigatória")
        String senha,

        @Schema(description = "Telefone de contato", example = "11999999999")
        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone,

        @Schema(description = "Aceite do termo de consentimento de dados (LGPD). Obrigatório e deve ser true; "
                + "ver docs/termo-consentimento-lgpd.md", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean consentimentoLgpd
) {
}
