package com.medcare.senior.domain.notificacao;

/**
 * Situação do aviso de uma dose não confirmada (tabela evento_notificacao, RN-010).
 */
public enum StatusEventoNotificacao {

    /** Criado e ainda não entregue; inclui a falha temporária, que a retentativa reprocessa. */
    PENDENTE,

    /** Aceito pelo provedor de push. */
    ENVIADO,

    /** Desistência definitiva, com o motivo em {@link MotivoFalhaNotificacao}. */
    FALHA
}
