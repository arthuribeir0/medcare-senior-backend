package com.medcare.senior.domain.exception;

/** Pedido de outro código antes de 1 minuto do anterior. */
public class EsperaParaReenviarException extends RuntimeException {

    private final long segundos;

    public EsperaParaReenviarException(long segundos) {
        super("Aguarde " + segundos + " segundos para pedir outro código.");
        this.segundos = segundos;
    }

    public long getSegundos() {
        return segundos;
    }
}
