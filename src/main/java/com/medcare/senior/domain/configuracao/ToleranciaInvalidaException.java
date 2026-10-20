package com.medcare.senior.domain.configuracao;

/**
 * Tolerância fora de [5, 120] minutos. Normalmente barrada antes pela validação do corpo da requisição; esta é a
 * garantia do domínio para qualquer outro caminho.
 */
public class ToleranciaInvalidaException extends RuntimeException {

    public ToleranciaInvalidaException() {
        super("A tolerância deve ficar entre " + Tolerancia.MINIMO_MINUTOS + " e " + Tolerancia.MAXIMO_MINUTOS
                + " minutos");
    }
}
