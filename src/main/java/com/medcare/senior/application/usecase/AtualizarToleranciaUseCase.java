package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.configuracao.ConfiguracaoAlertaRepositoryPort;
import com.medcare.senior.domain.configuracao.Tolerancia;
import com.medcare.senior.domain.configuracao.ToleranciaInvalidaException;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.springframework.stereotype.Service;

/**
 * Grava a tolerância do cuidador (RN-009). Vale para os avisos AINDA NÃO enviados: a varredura usa a tolerância do
 * momento em que roda, e um aviso já enviado (evento de notificação criado) nunca é reenviado por causa da mudança.
 * A listagem e a confirmação de doses passam a usar o novo valor imediatamente.
 */
@Service
public class AtualizarToleranciaUseCase {

    private final ConfiguracaoAlertaRepositoryPort configuracoes;
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;

    public AtualizarToleranciaUseCase(ConfiguracaoAlertaRepositoryPort configuracoes,
                                      CacheDeRelatorioDiarioPort cacheDeRelatorio) {
        this.configuracoes = configuracoes;
        this.cacheDeRelatorio = cacheDeRelatorio;
    }

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT (a existência da conta é conferida antes, na
     *                   sessão: conta excluída responde 401 SESSAO_INVALIDA)
     * @return a tolerância gravada, em minutos
     * @throws ToleranciaInvalidaException se estiver fora de [5, 120]
     */
    public int executar(Long cuidadorId, int toleranciaMinutos) {
        configuracoes.salvarToleranciaMinutos(cuidadorId, Tolerancia.validar(toleranciaMinutos));
        // A tolerância muda o aviso e o limite de confirmação de todas as doses dos idosos do cuidador
        cacheDeRelatorio.descartarDoCuidador(cuidadorId);
        return toleranciaMinutos;
    }
}
