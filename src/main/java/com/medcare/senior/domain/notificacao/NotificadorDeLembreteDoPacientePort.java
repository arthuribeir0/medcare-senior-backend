package com.medcare.senior.domain.notificacao;

/**
 * Envia ao aparelho do paciente o lembrete de uma dose ainda PENDENTE. Devolve o resultado classificado para quem chama
 * atualizar o evento de notificação, como o {@link NotificadorDeDoseNaoTomadaPort}. Implementações não devem lançar
 * exceção por falha de entrega; se lançarem, quem chama trata como falha temporária.
 */
public interface NotificadorDeLembreteDoPacientePort {

    ResultadoEnvioPush notificar(LembreteDoPacienteNotificacao notificacao);
}
