package com.medcare.senior.domain.exception;

/**
 * Decisão sobre um pedido de desconexão que já não está aguardando: foi decidido de outro jeito, cancelado pelo
 * paciente ou expirou. Responde 409 PEDIDO_ENCERRADO.
 */
public class PedidoDesconexaoEncerradoException extends RuntimeException {

    public PedidoDesconexaoEncerradoException() {
        super("Este pedido já foi decidido, cancelado ou expirou.");
    }
}
