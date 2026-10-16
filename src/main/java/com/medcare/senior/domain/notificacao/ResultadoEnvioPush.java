package com.medcare.senior.domain.notificacao;

/**
 * Resultado de um envio de push, já classificado para quem chama decidir o que fazer.
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
     * Falha que pode passar (provedor indisponível, cota, rede, erro interno ou de configuração). Pode ser tentada
     * de novo mais tarde; a retentativa ainda não existe.
     */
    FALHA_TEMPORARIA
}
