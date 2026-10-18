package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.historico.Pagina;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página do histórico de doses (UC-011), da mais recente para a mais antiga")
public record HistoricoResponseDTO(
        @Schema(description = "Doses da página (mesmo formato da listagem de doses); vazia quando não há registros")
        List<DoseResponseDTO> conteudo,
        @Schema(description = "Número da página, a partir de 0", example = "0")
        int pagina,
        @Schema(description = "Tamanho pedido", example = "20")
        int tamanho,
        @Schema(example = "57")
        long totalElementos,
        @Schema(example = "3")
        int totalPaginas
) {

    public static HistoricoResponseDTO from(Pagina<DoseAvaliada> pagina) {
        return new HistoricoResponseDTO(pagina.conteudo().stream().map(DoseResponseDTO::from).toList(), pagina.pagina(),
                pagina.tamanho(), pagina.totalElementos(), pagina.totalPaginas());
    }
}
