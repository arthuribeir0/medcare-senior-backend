package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Senha nova, com o código enviado ao e-mail")
public record RedefinirSenhaRequestDTO(

        @Schema(description = "E-mail da conta", example = "ana@email.com")
        @NotBlank(message = "O e-mail é obrigatório")
        @Size(max = 255, message = "O e-mail deve ter no máximo 255 caracteres")
        String email,

        @Schema(description = "Código recebido no e-mail", example = "482915")
        @NotBlank(message = "O código é obrigatório")
        @Size(max = 12, message = "O código deve ter 6 dígitos")
        String codigo,

        @Schema(description = "Mínimo de 8 caracteres, com maiúscula, minúscula e número", example = "SenhaNova1")
        @NotBlank(message = "A senha nova é obrigatória")
        @Size(max = 72, message = "A senha deve ter no máximo 72 caracteres")
        String novaSenha
) {
}
