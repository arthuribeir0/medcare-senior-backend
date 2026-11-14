package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.desconexao.NotificadorDePedidoDeDesconexaoPort;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Aviso por push de que um paciente pediu para sair da conta. Ativo só com app.fcm.enabled=true; desligado, vale o
 * LogNotificadorDePedidoDeDesconexaoAdapter. Mesmo tratamento do aviso de dose: sem token, nada é enviado; token
 * inválido é descartado; nunca lança exceção por falha de entrega. Logs só com IDs.
 *
 * <p>Payload de dados: tipo, pacienteId e pedidoId, para o app abrir o pedido ao tocar na notificação.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class PushNotificadorDePedidoDeDesconexaoAdapter implements NotificadorDePedidoDeDesconexaoPort {

    static final String TITULO = "Pedido para sair do app";
    static final String TIPO = "PEDIDO_DESCONEXAO";

    private static final Logger log = LoggerFactory.getLogger(PushNotificadorDePedidoDeDesconexaoAdapter.class);

    private final CuidadorRepositoryPort cuidadorRepository;
    private final NotificacaoPushPort notificacaoPush;

    public PushNotificadorDePedidoDeDesconexaoAdapter(CuidadorRepositoryPort cuidadorRepository,
                                                      NotificacaoPushPort notificacaoPush) {
        this.cuidadorRepository = cuidadorRepository;
        this.notificacaoPush = notificacaoPush;
    }

    @Override
    public ResultadoEnvioPush notificar(PedidoDesconexaoNotificacao notificacao) {
        Optional<String> token = cuidadorRepository.findById(notificacao.cuidadorId())
                .map(Cuidador::getFcmToken)
                .filter(valor -> !valor.isBlank());
        if (token.isEmpty()) {
            log.info("Cuidador {} sem token de dispositivo; aviso do pedido de desconexão {} não enviado",
                    notificacao.cuidadorId(), notificacao.pedidoId());
            return ResultadoEnvioPush.SEM_TOKEN;
        }
        ResultadoEnvioPush resultado = notificacaoPush.enviar(token.get(), TITULO, corpo(notificacao), dados(notificacao));
        if (resultado == ResultadoEnvioPush.TOKEN_INVALIDO) {
            cuidadorRepository.removerFcmTokenSeIgual(notificacao.cuidadorId(), token.get());
        }
        log.info("Aviso do pedido de desconexão {} ao cuidador {}: {}", notificacao.pedidoId(), notificacao.cuidadorId(),
                resultado);
        return resultado;
    }

    static String corpo(PedidoDesconexaoNotificacao notificacao) {
        return notificacao.nomePaciente() + " pediu para sair da conta no celular. Toque para autorizar ou recusar.";
    }

    static Map<String, String> dados(PedidoDesconexaoNotificacao notificacao) {
        return Map.of(
                "tipo", TIPO,
                "pacienteId", String.valueOf(notificacao.pacienteId()),
                "pedidoId", String.valueOf(notificacao.pedidoId()));
    }
}
