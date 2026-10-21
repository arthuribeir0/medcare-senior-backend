package com.medcare.senior.domain.exception;

/**
 * Parâmetro de consulta ou campo com valor fora da regra (ex.: período com início depois do fim, mensagem vazia).
 * Responde 400 VALIDACAO com o campo, no mesmo formato da validação do corpo.
 */
public class ParametroInvalidoException extends RuntimeException {

    private final String campo;

    public ParametroInvalidoException(String campo, String mensagem) {
        super(mensagem);
        this.campo = campo;
    }

    public String getCampo() {
        return campo;
    }
}
