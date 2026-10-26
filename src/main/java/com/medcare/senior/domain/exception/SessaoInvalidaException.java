package com.medcare.senior.domain.exception;

/**
 * O token é válido, mas o usuário dele não existe mais (ex.: paciente cuja conta do cuidador foi excluída, com o token
 * de 180 dias ainda válido, ou cuidador que excluiu a conta). Responde 401 SESSAO_INVALIDA para o app descartar a
 * sessão: o paciente volta ao pareamento e o cuidador ao login.
 */
public class SessaoInvalidaException extends RuntimeException {

    public SessaoInvalidaException() {
        super("Sessão inválida. Conecte este aparelho de novo.");
    }

    private SessaoInvalidaException(String mensagem) {
        super(mensagem);
    }

    /**
     * Token de cuidador cuja conta não existe mais.
     */
    public static SessaoInvalidaException doCuidador() {
        return new SessaoInvalidaException("Sessão inválida. Entre na sua conta de novo.");
    }
}
