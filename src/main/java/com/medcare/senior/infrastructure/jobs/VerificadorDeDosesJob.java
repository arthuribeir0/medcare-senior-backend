package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto (padrão), marca como NAO_TOMADA as doses vencidas e avisa os cuidadores. Só agenda; a regra fica no
 * VerificarDosesVencidasUseCase.
 *
 * <p>@SchedulerLock (ShedLock): com várias instâncias, só uma verifica por vez.
 * <ul>
 *   <li>lockAtMostFor = 5 min: um lote de até 500 doses termina bem antes; se a instância morrer no meio, a
 *       verificação volta em no máximo 5 minutos.</li>
 *   <li>lockAtLeastFor = 30 s: menor que o intervalo de 1 minuto, para não pular a execução seguinte, mas suficiente
 *       para uma instância com o relógio um pouco atrasado não repetir a mesma rodada.</li>
 * </ul>
 * O UPDATE atômico (status = PENDENTE) garante que, mesmo sem a trava, uma dose nunca é marcada nem avisada duas vezes.
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
