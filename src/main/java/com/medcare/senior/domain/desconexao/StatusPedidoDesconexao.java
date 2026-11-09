package com.medcare.senior.domain.desconexao;

/**
 * Situação do pedido do paciente para sair da conta no aparelho. Gravado pelo nome: não renomeie.
 */
public enum StatusPedidoDesconexao {
    /** Aguardando o cuidador. No máximo um por paciente. */
    PENDENTE,
    /** O cuidador autorizou: o aparelho do paciente foi desconectado. */
    AUTORIZADO,
    /** O cuidador recusou: o paciente continua conectado. */
    RECUSADO,
    /** Ninguém decidiu dentro da validade. O paciente pode pedir de novo. */
    EXPIRADO,
    /** O próprio paciente desistiu antes da decisão. */
    CANCELADO
}
