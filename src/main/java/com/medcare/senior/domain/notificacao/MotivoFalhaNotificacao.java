package com.medcare.senior.domain.notificacao;

/**
 * Por que um aviso foi abandonado (evento_notificacao.motivo_falha). Gravado pelo nome: não renomeie.
 */
public enum MotivoFalhaNotificacao {

    /**
     * O provedor recusou o token do aparelho (do cuidador ou, no lembrete, do paciente): app desinstalado, token expirado
     * ou malformado. O token é descartado.
     */
    TOKEN_INVALIDO,

    /** O destinatário (cuidador ou paciente) não tem aparelho registrado, ou não existe mais. */
    SEM_TOKEN,

    /** A dose deixou de estar PENDENTE antes do reenvio (confirmada ou encerrada): o aviso perdeu o sentido. */
    DOSE_JA_REGISTRADA,

    /** Todas as tentativas permitidas falharam temporariamente. */
    TENTATIVAS_ESGOTADAS,

    /** O push estava desligado (FCM_ENABLED=false): o aviso ficou só no log e não foi entregue. Sem retentativa. */
    PUSH_DESLIGADO,

    /**
     * Lembrete ao paciente que perdeu a hora antes do reenvio: a dose chegou ao aviso ao cuidador. Dali em diante quem
     * cuida é o aviso ao cuidador; o lembrete não é mais enviado.
     */
    FORA_DA_JANELA
}
