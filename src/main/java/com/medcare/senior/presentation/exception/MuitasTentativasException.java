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
