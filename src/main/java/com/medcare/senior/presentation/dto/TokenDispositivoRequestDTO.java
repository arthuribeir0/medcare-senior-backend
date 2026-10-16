package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Token de dispositivo (Firebase Cloud Messaging) do aparelho do cuidador")
public record TokenDispositivoRequestDTO(

        @Schema(description = "Token de registro FCM obtido pelo app no aparelho", example = "dQw4w9WgXcQ:APA91bH...",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O token do dispositivo é obrigatório")
        @Size(max = 512, message = "O token do dispositivo deve ter no máximo 512 caracteres")
        String token
) {
}
