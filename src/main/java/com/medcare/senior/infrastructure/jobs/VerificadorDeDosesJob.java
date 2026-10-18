package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto (padrão), avisa os cuidadores das doses que passaram da tolerância (a dose continua PENDENTE) e
 * encerra como NAO_TOMADA as que passaram do encerramento. Só agenda; a regra fica no VerificarDosesVencidasUseCase.
 *
 * <p>@SchedulerLock (ShedLock): com várias instâncias, só uma verifica por vez.
 * <ul>
 *   <li>lockAtMostFor = 5 min: dois lotes de até 500 doses terminam bem antes; se a instância morrer no meio, a
 *       verificação volta em no máximo 5 minutos.</li>
 *   <li>lockAtLeastFor = 30 s: menor que o intervalo de 1 minuto, para não pular a execução seguinte, mas suficiente
 *       para uma instância com o relógio um pouco atrasado não repetir a mesma rodada.</li>
 * </ul>
 * Mesmo sem a trava, uma dose nunca é encerrada duas vezes (UPDATE condicionado a status = PENDENTE) nem avisada duas
 * vezes (evento único por dose e reivindicação atômica da tentativa).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class VerificadorDeDosesJob {

    private final VerificarDosesVencidasUseCase verificarDosesVencidasUseCase;

    @Scheduled(cron = "${app.jobs.verificar-doses.cron:0 * * * * *}", zone = "${app.timezone:America/Sao_Paulo}")
    @SchedulerLock(name = "verificar-doses", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void executar() {
        verificarDosesVencidasUseCase.executar();
    }
}
