/*
 * Porta (interface) do domínio para código por e-mail; a implementação fica na infraestrutura. Operações: buscar,
 * salvar, registrarTentativa, apagar.
 */
package com.medcare.senior.domain.email;

import java.util.Optional;

public interface CodigoPorEmailRepositoryPort {

    Optional<CodigoPorEmail> buscar(Long cuidadorId, FinalidadeDoCodigo finalidade);

    /** Grava o código, substituindo o anterior do mesmo cuidador e finalidade (tentativas voltam a zero). */
    void salvar(CodigoPorEmail codigo);

    /** Soma uma tentativa errada, de forma atômica (duas requisições ao mesmo tempo contam as duas). */
    void registrarTentativa(Long cuidadorId, FinalidadeDoCodigo finalidade);

    void apagar(Long cuidadorId, FinalidadeDoCodigo finalidade);
}
