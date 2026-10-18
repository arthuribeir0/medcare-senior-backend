package com.medcare.senior.domain.exception;

/**
 * O token é válido, mas o usuário dele não existe mais (ex.: idoso cuja conta do cuidador foi excluída, com o token
 * de 180 dias ainda válido). Responde 401 para o app voltar ao pareamento.
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException() {
        super("Sessão inválida. Conecte este aparelho de novo.");
    }
}
