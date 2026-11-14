/*
 * DTO de entrada (corpo da requisição): contrato JSON de código e-mail. Campos: codigo. A API devolve DTOs, nunca
 * as entidades.
 */
package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Código de 6 dígitos enviado ao e-mail do cuidador")
public record CodigoEmailRequestDTO(

        @Schema(description = "Código recebido no e-mail", example = "482915")
        @NotBlank(message = "O código é obrigatório")
        @Size(max = 12, message = "O código deve ter 6 dígitos")
        String codigo
) {
}
