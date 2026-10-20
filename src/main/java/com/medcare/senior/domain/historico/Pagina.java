package com.medcare.senior.domain.historico;

import java.util.List;
import java.util.function.Function;

/**
 * Uma página de resultados.
 *
 * @param pagina         número da página, a partir de 0
 * @param tamanho        tamanho pedido
 * @param totalElementos total em todas as páginas
 */
public record Pagina<T>(List<T> conteudo, int pagina, int tamanho, long totalElementos) {

    public int totalPaginas() {
        return (int) ((totalElementos + tamanho - 1) / tamanho);
    }

    public <R> Pagina<R> map(Function<T, R> conversao) {
        return new Pagina<>(conteudo.stream().map(conversao).toList(), pagina, tamanho, totalElementos);
    }
}
