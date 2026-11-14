/*
 * DTO de saída (corpo da resposta): contrato JSON de pedido desconexão pendente. Campos: id, pacienteId,
 * pacienteNome, criadoEm, expiraEm. A API devolve DTOs, nunca as entidades.
 */
package com.medcare.senior.presentation.dto;

import com.medcare.senior.application.usecase.ListarPedidosDesconexaoPendentesUseCase.PedidoPendente;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Pedido de um paciente do cuidador para sair da conta, aguardando a decisão")
public record PedidoDesconexaoPendenteResponseDTO(
        @Schema(description = "ID do pedido", example = "7")
        Long id,

        @Schema(description = "ID do paciente", example = "10")
        Long pacienteId,

        @Schema(description = "Nome do paciente", example = "Maria Oliveira")
        String pacienteNome,

        @Schema(description = "Quando o paciente pediu", example = "2026-11-09T14:30:00")
        LocalDateTime criadoEm,

        @Schema(description = "Prazo para decidir; depois disso o pedido expira", example = "2026-11-10T14:30:00")
        LocalDateTime expiraEm
) {

    public static PedidoDesconexaoPendenteResponseDTO from(PedidoPendente pendente) {
        return new PedidoDesconexaoPendenteResponseDTO(pendente.pedido().id(), pendente.pedido().pacienteId(),
                pendente.pacienteNome(), pendente.pedido().criadoEm(), pendente.pedido().expiraEm());
    }
}
