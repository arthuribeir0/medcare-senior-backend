package com.medcare.senior.domain.notificacao;

/**
 * Envia uma notificação push para um aparelho. Não lança exceção por falha de entrega: devolve o resultado
 * classificado. Implementações nunca devem registrar o token em log (dado pessoal).
 */
public interface NotificacaoPushPort {

    ResultadoEnvioPush enviar(String tokenDestino, String titulo, String corpo);
}
