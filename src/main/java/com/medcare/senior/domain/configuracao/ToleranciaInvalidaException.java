package com.medcare.senior.domain.configuracao;

/**
 * Tolerância fora de [5, 120] minutos. Normalmente barrada antes pela validação do corpo da requisição; esta é a
 * garantia do domínio para qualquer outro caminho.
 */
public class ToleranciaInvalidaException extends RuntimeException {

    public ToleranciaInvalidaException() {
        this(Tolerancia.MINIMO_MINUTOS);
    }

    /** @param minimo menor tolerância aceita no momento (1 no modo de teste) */
    public ToleranciaInvalidaException(int minimo) {
        super("A tolerância deve ficar entre " + minimo + " e " + Tolerancia.MAXIMO_MINUTOS + " minutos");
    }
}
