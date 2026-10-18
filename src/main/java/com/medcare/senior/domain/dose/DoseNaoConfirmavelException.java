package com.medcare.senior.domain.dose;

import lombok.Getter;

/**
 * A dose não pode ser confirmada agora: já registrada (inclusive por uma confirmação concorrente), cedo demais ou
 * depois do encerramento da janela. Mapeada para HTTP 409 com um código por motivo.
 */
@Getter
public class DoseNaoConfirmavelException extends RuntimeException {

    private final Confirmabilidade motivo;

    public DoseNaoConfirmavelException(Confirmabilidade motivo) {
        super(switch (motivo) {
            case MUITO_CEDO -> "Ainda é cedo para confirmar esta dose.";
            case ENCERRADA -> "O prazo para confirmar esta dose terminou.";
            case JA_REGISTRADA, OK -> "Esta dose já foi registrada.";
        });
        this.motivo = motivo == Confirmabilidade.OK ? Confirmabilidade.JA_REGISTRADA : motivo;
    }
}
