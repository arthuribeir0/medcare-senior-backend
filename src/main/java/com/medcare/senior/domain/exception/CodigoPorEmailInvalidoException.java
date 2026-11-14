package com.medcare.senior.domain.exception;

/**
 * Código do e-mail errado, vencido, já usado ou bloqueado depois de 5 tentativas (e, na redefinição de senha, e-mail
 * sem conta: a resposta é a mesma, para não revelar quais e-mails estão cadastrados).
 */
public class CodigoPorEmailInvalidoException extends RuntimeException {

    public CodigoPorEmailInvalidoException(String mensagem) {
        super(mensagem);
    }

    public static CodigoPorEmailInvalidoException incorreto(int tentativasRestantes) {
        return new CodigoPorEmailInvalidoException(tentativasRestantes == 1
                ? "Código incorreto. Você tem mais 1 tentativa."
                : "Código incorreto. Você tem mais " + tentativasRestantes + " tentativas.");
    }

    public static CodigoPorEmailInvalidoException pecaOutro() {
        return new CodigoPorEmailInvalidoException("Este código não vale mais. Peça um novo código.");
    }
}
