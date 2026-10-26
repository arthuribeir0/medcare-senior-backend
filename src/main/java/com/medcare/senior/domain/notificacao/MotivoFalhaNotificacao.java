package com.medcare.senior.domain.notificacao;

/**
 * Por que um aviso foi abandonado (evento_notificacao.motivo_falha). Gravado pelo nome: não renomeie.
 */
public enum MotivoFalhaNotificacao {

    /** O provedor recusou o token do aparelho do cuidador (app desinstalado, token expirado ou malformado). */
    TOKEN_INVALIDO,

    /** O cuidador não tem aparelho registrado (ou a conta não existe mais). */
    SEM_TOKEN,

    /** A dose deixou de estar PENDENTE antes do reenvio (confirmada ou encerrada): o aviso perdeu o sentido. */
    DOSE_JA_REGISTRADA,

    /** Todas as tentativas permitidas falharam temporariamente. */
    TENTATIVAS_ESGOTADAS,

    /** O push estava desligado (FCM_ENABLED=false): o aviso ficou só no log e não foi entregue. Sem retentativa. */
    PUSH_DESLIGADO
}
