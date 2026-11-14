package com.medcare.senior.domain.email;

/** Envio do código ao e-mail do cuidador (SMTP em produção; log no desenvolvimento e nos testes). */
public interface EnviadorDeEmailPort {

    /**
     * Nunca lança exceção por falha de entrega: o cuidador pode pedir outro código.
     *
     * @return {@code true} se o e-mail foi entregue ao servidor de envio
     */
    boolean enviarCodigo(String destinatario, String nome, FinalidadeDoCodigo finalidade, String codigo);
}
