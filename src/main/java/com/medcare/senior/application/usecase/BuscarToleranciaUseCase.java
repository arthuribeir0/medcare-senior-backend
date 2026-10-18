package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.configuracao.ConfiguracaoAlertaRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import org.springframework.stereotype.Service;

/**
 * Tolerância efetiva do cuidador, em minutos: a própria (configuração de alerta) ou, sem ela, a padrão da aplicação.
 */
@Service
public class BuscarToleranciaUseCase {

    private final ConfiguracaoAlertaRepositoryPort configuracoes;
    private final PoliticaDeJanela politicaDeJanela;

    public BuscarToleranciaUseCase(ConfiguracaoAlertaRepositoryPort configuracoes, PoliticaDeJanela politicaDeJanela) {
        this.configuracoes = configuracoes;
        this.politicaDeJanela = politicaDeJanela;
    }

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public int executar(Long cuidadorId) {
        return configuracoes.findToleranciaMinutos(cuidadorId)
                .orElseGet(() -> Math.toIntExact(politicaDeJanela.toleranciaPadrao().toMinutes()));
    }
}
