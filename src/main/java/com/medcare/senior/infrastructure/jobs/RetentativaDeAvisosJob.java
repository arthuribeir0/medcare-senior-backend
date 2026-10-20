package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.ReenviarAvisosPendentesUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada 5 minutos (padrão; no segundo 30, para não coincidir com a varredura de doses), reenvia os avisos de dose não
 * confirmada que falharam temporariamente (RN-010). Só agenda; a regra fica no ReenviarAvisosPendentesUseCase.
 *
 * <p>@SchedulerLock próprio (ShedLock): com várias instâncias, só uma reprocessa por vez.
 * <ul>
 *   <li>lockAtMostFor = 4 min: um lote de até 200 eventos termina bem antes; menor que o intervalo, para a próxima
 *       execução não ser pulada se a instância morrer no meio.</li>
 *   <li>lockAtLeastFor = 30 s: evita que uma instância com o relógio atrasado repita a mesma rodada.</li>
 * </ul>
 * Mesmo sem a trava, um aviso nunca é enviado duas vezes: cada tentativa é reivindicada por um UPDATE condicionado às
 * tentativas lidas.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class RetentativaDeAvisosJob {

    private final ReenviarAvisosPendentesUseCase reenviarAvisosPendentesUseCase;

    @Scheduled(cron = "${app.jobs.retentativa-avisos.cron:30 */5 * * * *}", zone = "${app.timezone:America/Sao_Paulo}")
    @SchedulerLock(name = "retentativa-avisos", lockAtMostFor = "PT4M", lockAtLeastFor = "PT30S")
    public void executar() {
        reenviarAvisosPendentesUseCase.executar();
    }
}
