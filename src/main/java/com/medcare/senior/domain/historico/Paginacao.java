package com.medcare.senior.domain.historico;

import com.medcare.senior.domain.exception.ParametroInvalidoException;

/**
 * Página pedida numa consulta paginada (histórico, mensagens), já validada.
 *
 * @param pagina  a partir de 0
 * @param tamanho de 1 a {@link #TAMANHO_MAXIMO}
 */
public record Paginacao(int pagina, int tamanho) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    /**
     * Aplica os padrões (página 0, tamanho 20) e valida.
     *
     * @throws ParametroInvalidoException se a página for negativa ou o tamanho ficar fora de 1 a 100
     */
    public static Paginacao de(Integer pagina, Integer tamanho) {
        int numeroDaPagina = pagina != null ? pagina : 0;
        if (numeroDaPagina < 0) {
            throw new ParametroInvalidoException("page", "A página começa em 0");
        }
        int tamanhoDaPagina = tamanho != null ? tamanho : TAMANHO_PADRAO;
        if (tamanhoDaPagina < 1 || tamanhoDaPagina > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException("size", "O tamanho da página deve ficar entre 1 e " + TAMANHO_MAXIMO);
        }
        return new Paginacao(numeroDaPagina, tamanhoDaPagina);
    }
}
