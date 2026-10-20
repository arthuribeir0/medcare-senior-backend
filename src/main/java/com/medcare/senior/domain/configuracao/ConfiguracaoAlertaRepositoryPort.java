package com.medcare.senior.domain.configuracao;

import java.util.Optional;

/**
 * Configuração de alerta do cuidador (V13). Sem configuração própria vale a tolerância padrão da aplicação.
 */
public interface ConfiguracaoAlertaRepositoryPort {

    /**
     * @return a tolerância própria do cuidador, em minutos, ou vazio se ele usa a padrão
     */
    Optional<Integer> findToleranciaMinutos(Long cuidadorId);

    /**
     * Grava (ou troca) a tolerância do cuidador em um único comando ({@code INSERT ... ON CONFLICT DO UPDATE}).
     */
    void salvarToleranciaMinutos(Long cuidadorId, int toleranciaMinutos);
}
