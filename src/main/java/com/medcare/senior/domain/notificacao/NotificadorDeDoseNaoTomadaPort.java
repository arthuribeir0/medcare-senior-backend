package com.medcare.senior.domain.notificacao;

/**
 * Avisa o cuidador de que uma dose não foi confirmada dentro da tolerância (a dose continua PENDENTE). Devolve o
 * resultado classificado para quem chama atualizar o evento de notificação. Implementações não devem lançar exceção
 * por falha de entrega; se lançarem, quem chama trata como falha temporária e segue para as demais doses.
 */
public interface NotificadorDeDoseNaoTomadaPort {

    ResultadoEnvioPush notificar(DoseNaoTomadaNotificacao notificacao);
}
