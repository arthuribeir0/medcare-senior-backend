package com.medcare.senior.domain.notificacao;

/**
 * Avisa o cuidador de que uma dose passou a NAO_TOMADA. Só é chamado depois que a mudança de status foi gravada.
 * Implementações não devem lançar exceção por falha de entrega; se lançarem, o verificador registra e segue.
 */
public interface NotificadorDeDoseNaoTomadaPort {

    void notificar(DoseNaoTomadaNotificacao notificacao);
}
