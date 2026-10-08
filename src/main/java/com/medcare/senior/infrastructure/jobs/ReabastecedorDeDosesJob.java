package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.ReabastecerDosesUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Uma vez por dia (padrão 00:05 no fuso app.timezone), empurra a janela de doses dos tratamentos ativos.
 * Só agenda; a regra fica no ReabastecerDosesUseCase.
 *
 * <p>Limitação: roda em todas as instâncias da aplicação. Com mais de uma instância, a geração continua correta
 * (idempotente + índice único), mas instâncias concorrentes podem registrar falhas por violação do índice único.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ReabastecedorDeDosesJob {

    private final ReabastecerDosesUseCase reabastecerDosesUseCase;

    @Scheduled(cron = "${app.jobs.reabastecer-doses.cron:0 5 0 * * *}", zone = "${app.timezone:America/Sao_Paulo}")
    public void executar() {
        reabastecerDosesUseCase.executar();
    }
}
