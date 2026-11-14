package com.medcare.senior.domain.desconexao;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Pedido do paciente para sair da conta no aparelho; só o cuidador vinculado autoriza ou recusa. Horários no fuso da
 * aplicação.
 *
 * @param decididoEm            quando deixou de estar PENDENTE (decisão, cancelamento ou expiração)
 * @param decididoPorCuidadorId quem autorizou ou recusou; {@code null} nos demais casos
 */
public record PedidoDesconexao(Long id, Long pacienteId, StatusPedidoDesconexao status, LocalDateTime criadoEm,
                               LocalDateTime expiraEm, LocalDateTime decididoEm, Long decididoPorCuidadorId) {

    /**
     * Pedido novo, PENDENTE até {@code agora + validade}.
     */
    public static PedidoDesconexao novo(Long pacienteId, LocalDateTime agora, Duration validade) {
        return new PedidoDesconexao(null, pacienteId, StatusPedidoDesconexao.PENDENTE, agora, agora.plus(validade),
                null, null);
    }

    /**
     * Status valendo em {@code agora}: um PENDENTE que passou do prazo já conta como EXPIRADO, mesmo antes de o job
     * gravar isso. Vence exatamente em {@code expiraEm}.
     */
    public StatusPedidoDesconexao statusEm(LocalDateTime agora) {
        if (status == StatusPedidoDesconexao.PENDENTE && !agora.isBefore(expiraEm)) {
            return StatusPedidoDesconexao.EXPIRADO;
        }
        return status;
    }

    public boolean pendenteEm(LocalDateTime agora) {
        return statusEm(agora) == StatusPedidoDesconexao.PENDENTE;
    }

    /**
     * O mesmo pedido com o status valendo em {@code agora} (para a resposta da API).
     */
    public PedidoDesconexao vistoEm(LocalDateTime agora) {
        StatusPedidoDesconexao atual = statusEm(agora);
        if (atual == status) {
            return this;
        }
        return new PedidoDesconexao(id, pacienteId, atual, criadoEm, expiraEm, expiraEm, decididoPorCuidadorId);
    }
}
