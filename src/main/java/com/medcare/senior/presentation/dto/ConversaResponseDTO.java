package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.mensagem.ResumoConversa;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Uma conversa: o idoso, a última mensagem e quantas mensagens quem chama ainda não leu")
public record ConversaResponseDTO(
        @Schema(example = "10")
        Long idosoId,

        @Schema(example = "José Souza")
        String idosoNome,

        @Schema(description = "Última mensagem (null se a conversa ainda não tem mensagens)", nullable = true)
        UltimaMensagemDTO ultimaMensagem,

        @Schema(description = "Mensagens do outro lado ainda não lidas por quem chama", example = "2")
        long naoLidas
) {

    @Schema(description = "Última mensagem da conversa")
    public record UltimaMensagemDTO(
            @Schema(example = "Já tomei, obrigado!")
            String conteudo,
            @Schema(example = "IDOSO")
            Remetente remetente,
            @Schema(example = "2026-10-20T08:10:00")
            LocalDateTime criadaEm
    ) {
    }

    public static ConversaResponseDTO from(ResumoConversa conversa) {
        ResumoConversa.UltimaMensagem ultima = conversa.ultimaMensagem();
        return new ConversaResponseDTO(conversa.idosoId(), conversa.idosoNome(),
                ultima == null ? null : new UltimaMensagemDTO(ultima.conteudo(), ultima.remetente(), ultima.criadaEm()),
                conversa.naoLidas());
    }
}
