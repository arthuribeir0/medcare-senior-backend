package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.mensagem.Mensagem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página da conversa, da mensagem mais recente para a mais antiga")
public record MensagensResponseDTO(
        @Schema(description = "Mensagens da página; vazia quando a conversa ainda não tem mensagens")
        List<MensagemResponseDTO> conteudo,
        @Schema(description = "Número da página, a partir de 0", example = "0")
        int pagina,
        @Schema(description = "Tamanho pedido", example = "20")
        int tamanho,
        @Schema(example = "42")
        long totalElementos,
        @Schema(example = "3")
        int totalPaginas
) {

    public static MensagensResponseDTO from(Pagina<Mensagem> pagina) {
        return new MensagensResponseDTO(pagina.conteudo().stream().map(MensagemResponseDTO::from).toList(),
                pagina.pagina(), pagina.tamanho(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
