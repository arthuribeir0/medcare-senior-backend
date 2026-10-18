package com.medcare.senior.domain.notificacao;

import java.util.Map;

/**
 * Envia uma notificação push para um aparelho. Não lança exceção por falha de entrega: devolve o resultado
 * classificado. Implementações nunca devem registrar o token em log (dado pessoal).
 */
public interface NotificacaoPushPort {

    /**
     * @param dados pares chave/valor entregues ao app junto com a notificação (ex.: tipo e IDs para abrir a tela
     *              certa). Nunca coloque aqui dado de saúde ou pessoal além de IDs.
     */
    ResultadoEnvioPush enviar(String tokenDestino, String titulo, String corpo, Map<String, String> dados);
}
