package com.medcare.senior.domain.desconexao;

import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;

/**
 * Avisa o cuidador de que um paciente pediu para sair da conta (push quando o FCM está ligado; só log quando não).
 * Uma tentativa só: o pedido aparece no Painel do cuidador de qualquer forma. Implementações não devem lançar exceção
 * por falha de entrega; se lançarem, quem chama segue sem derrubar o pedido.
 */
public interface NotificadorDePedidoDeDesconexaoPort {

    ResultadoEnvioPush notificar(PedidoDesconexaoNotificacao notificacao);
}
