package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * O paciente desiste do pedido de desconexão antes da decisão do cuidador. Idempotente: sem pedido pendente (já
 * decidido, expirado, cancelado ou inexistente), nada muda.
 */
@Service
@RequiredArgsConstructor
public class CancelarPedidoDesconexaoUseCase {

    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final Clock clock;

    /**
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     * @return true se havia um pedido pendente e ele foi cancelado
     */
    public boolean executar(Long pacienteId) {
        return pedidoRepository.cancelarPendente(pacienteId, LocalDateTime.now(clock));
    }
}
