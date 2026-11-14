package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.LembrarPacienteDaDoseUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada minuto (padrão; no segundo 15, para não coincidir com a varredura de doses, no segundo 0, nem com a
 * retentativa, no segundo 30), reforça pelo push o lembrete das doses que o paciente ainda não confirmou. Só agenda; a
 * regra fica no LembrarPacienteDaDoseUseCase.
 *
 * <p>@SchedulerLock próprio (ShedLock): com várias instâncias, só uma lembra por vez.
 * <ul>
 *   <li>lockAtMostFor = 5 min: um lote de até 500 doses termina bem antes; se a instância morrer no meio, o lembrete
 *       volta em no máximo 5 minutos.</li>
 *   <li>lockAtLeastFor = 30 s: menor que o intervalo de 1 minuto, para não pular a execução seguinte, mas suficiente
 *       para uma instância com o relógio um pouco atrasado não repetir a mesma rodada.</li>
 * </ul>
 * Mesmo sem a trava, um lembrete nunca é enviado duas vezes: evento único por dose e tipo, e reivindicação atômica da
 * tentativa.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LembreteDoPacienteJob {

    private final LembrarPacienteDaDoseUseCase lembrarPacienteDaDoseUseCase;

    @Scheduled(cron = "${app.jobs.lembrete-paciente.cron:15 * * * * *}", zone = "${app.timezone:America/Sao_Paulo}")
    @SchedulerLock(name = "lembrete-paciente", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void executar() {
        lembrarPacienteDaDoseUseCase.executar();
    }
}
