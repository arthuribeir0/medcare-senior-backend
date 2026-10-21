package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.mensagem.Mensagem;
import com.medcare.senior.domain.mensagem.Remetente;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Mensagem da conversa de um idoso com o seu cuidador")
public record MensagemResponseDTO(
        @Schema(example = "301")
        Long id,

        @Schema(description = "Quem escreveu", example = "CUIDADOR")
        Remetente remetente,

        @Schema(example = "Bom dia! Lembre do remédio das 8h.")
        String conteudo,

        @Schema(description = "Envio (fuso da aplicação)", example = "2026-10-20T08:00:00")
        LocalDateTime criadaEm,

        @Schema(description = "Quando o destinatário leu (null = não lida)", example = "2026-10-20T08:05:00",
                nullable = true)
        LocalDateTime lidaEm
) {

    public static MensagemResponseDTO from(Mensagem mensagem) {
        return new MensagemResponseDTO(mensagem.id(), mensagem.remetente(), mensagem.conteudo(), mensagem.criadaEm(),
                mensagem.lidaEm());
    }
}
