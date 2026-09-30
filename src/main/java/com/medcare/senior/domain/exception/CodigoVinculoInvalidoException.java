package com.medcare.senior.domain.exception;

/**
 * Lançada quando o código de vínculo não existe, expirou ou já foi usado. A mensagem é propositalmente
 * genérica para não revelar qual dos três motivos causou a falha.
 */
public class CodigoVinculoInvalidoException extends RuntimeException {

    public CodigoVinculoInvalidoException() {
        super("Código inválido ou expirado");
    }
}
