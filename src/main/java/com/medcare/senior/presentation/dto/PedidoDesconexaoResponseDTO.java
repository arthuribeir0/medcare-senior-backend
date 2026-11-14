/*
 * DTO de saída (corpo da resposta): contrato JSON de pedido desconexão. Campos: id, pacienteId, status, criadoEm,
 * expiraEm, decididoEm. A API devolve DTOs, nunca as entidades.
 */
package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Pedido do paciente para sair da conta no aparelho")
public record PedidoDesconexaoResponseDTO(
        @Schema(description = "ID do pedido", example = "7")
        Long id,

        @Schema(description = "ID do paciente", example = "10")
        Long pacienteId,

        @Schema(description = "PENDENTE (aguardando o cuidador), AUTORIZADO, RECUSADO, EXPIRADO (ninguém decidiu no prazo) "
                + "ou CANCELADO (o paciente desistiu). Um pendente vencido já sai como EXPIRADO.", example = "PENDENTE")
        StatusPedidoDesconexao status,

        @Schema(description = "Quando o paciente pediu", example = "2026-11-09T14:30:00")
        LocalDateTime criadoEm,

        @Schema(description = "Prazo para o cuidador decidir", example = "2026-11-10T14:30:00")
        LocalDateTime expiraEm,

        @Schema(description = "Quando deixou de estar pendente; nulo enquanto PENDENTE", example = "2026-11-09T14:35:00",
                nullable = true)
        LocalDateTime decididoEm
) {

    public static PedidoDesconexaoResponseDTO from(PedidoDesconexao pedido) {
        return new PedidoDesconexaoResponseDTO(pedido.id(), pedido.pacienteId(), pedido.status(), pedido.criadoEm(),
                pedido.expiraEm(), pedido.decididoEm());
    }
}
