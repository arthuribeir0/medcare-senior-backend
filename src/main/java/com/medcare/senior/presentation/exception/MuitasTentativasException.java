/*
 * Exceção do limitador de tentativas: depois de 5 falhas por IP em 15 minutos a API responde 429 e informa em
 * quantos segundos libera.
 */
package com.medcare.senior.presentation.exception;

import lombok.Getter;

@Getter
public class MuitasTentativasException extends RuntimeException {

    private final long segundosAteLiberar;

    public MuitasTentativasException(long segundosAteLiberar) {
        super("Muitas tentativas inválidas. Tente novamente mais tarde.");
        this.segundosAteLiberar = segundosAteLiberar;
    }
}
