package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Marca como EXPIRADO os pedidos de desconexão que ninguém decidiu no prazo. Só arrumação: as leituras já tratam um
 * pendente vencido como expirado, e uma decisão depois do prazo nunca vale (o UPDATE confere o prazo).
 */
@Service
@RequiredArgsConstructor
public class ExpirarPedidosDesconexaoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExpirarPedidosDesconexaoUseCase.class);

    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final Clock clock;

    /**
     * @return quantos pedidos foram marcados como EXPIRADO
     */
    public int executar() {
        int expirados = pedidoRepository.expirarTodosVencidos(LocalDateTime.now(clock));
        if (expirados > 0) {
            log.info("{} pedido(s) de desconexão expirado(s)", expirados);
        }
        return expirados;
    }
}
