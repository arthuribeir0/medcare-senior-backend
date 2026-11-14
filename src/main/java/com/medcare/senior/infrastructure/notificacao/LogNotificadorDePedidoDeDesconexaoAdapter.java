package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.desconexao.NotificadorDePedidoDeDesconexaoPort;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoNotificacao;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Aviso do pedido de desconexão só em log, enquanto o FCM estiver desligado (padrão, testes e CI). Só IDs no log.
 * O cuidador ainda vê o pedido no Painel.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificadorDePedidoDeDesconexaoAdapter implements NotificadorDePedidoDeDesconexaoPort {

    private static final Logger log = LoggerFactory.getLogger(LogNotificadorDePedidoDeDesconexaoAdapter.class);

    @Override
    public ResultadoEnvioPush notificar(PedidoDesconexaoNotificacao notificacao) {
        log.info("Pedido de desconexão {} do paciente {}; push desligado, aviso ao cuidador {} registrado só em log",
                notificacao.pedidoId(), notificacao.pacienteId(), notificacao.cuidadorId());
        return ResultadoEnvioPush.PUSH_DESLIGADO;
    }
}
