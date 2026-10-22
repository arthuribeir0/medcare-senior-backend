package com.medcare.senior.domain.notificacao;

/**
 * Resultado de um envio de push, já classificado para quem chama decidir o que fazer com o evento de notificação.
 */
public enum ResultadoEnvioPush {

    /**
     * Aceito pelo provedor (ou validado, em dry-run).
     */
    ENVIADO,

    /**
     * O token não serve mais (app desinstalado, token expirado, de outro projeto ou malformado). Reenviar para o
     * mesmo token nunca vai funcionar: o token deve ser descartado.
     */
    TOKEN_INVALIDO,

    /**
     * Falha que pode passar (provedor indisponível, cota, rede, erro interno ou de configuração). O evento continua
     * PENDENTE para a retentativa.
     */
    FALHA_TEMPORARIA,

    /**
     * O cuidador não tem token de aparelho registrado (ou a conta não existe mais): não há para onde enviar.
     */
    SEM_TOKEN,

    /**
     * O push está desligado nesta instalação (app.fcm.enabled=false): o aviso só foi para o log e NÃO chegou ao
     * cuidador. Não adianta tentar de novo enquanto a configuração não mudar.
     */
    PUSH_DESLIGADO
}
