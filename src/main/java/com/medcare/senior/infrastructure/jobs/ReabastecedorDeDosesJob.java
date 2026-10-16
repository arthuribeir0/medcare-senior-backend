package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.ReabastecerDosesUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Uma vez por dia (padrão 00:05 no fuso app.timezone), empurra a janela de doses dos tratamentos ativos.
 * Só agenda; a regra fica no ReabastecerDosesUseCase.
 *
 * <p>Com mais de uma instância, o @SchedulerLock (ShedLock) garante que só uma delas execute:
 * <ul>
 *   <li>lockAtMostFor = 30 min: o reabastecimento leva segundos; se a instância morrer no meio, a trava se solta
 *       sozinha bem antes da próxima execução (24 h depois).</li>
 *   <li>lockAtLeastFor = 1 min: segura a trava mesmo se terminar rápido, para uma instância com o relógio um pouco
 *       atrasado não disparar a mesma execução logo depois.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ReabastecedorDeDosesJob {

    private final ReabastecerDosesUseCase reabastecerDosesUseCase;

    @Scheduled(cron = "${app.jobs.reabastecer-doses.cron:0 5 0 * * *}", zone = "${app.timezone:America/Sao_Paulo}")
    @SchedulerLock(name = "reabastecer-doses", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void executar() {
        reabastecerDosesUseCase.executar();
    }
}
