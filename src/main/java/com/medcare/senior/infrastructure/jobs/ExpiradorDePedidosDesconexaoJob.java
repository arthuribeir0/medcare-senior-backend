package com.medcare.senior.infrastructure.jobs;

import com.medcare.senior.application.usecase.ExpirarPedidosDesconexaoUseCase;
import lombok.RequiredArgsConstructor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A cada 5 minutos (padrão), marca como EXPIRADO os pedidos de desconexão vencidos. Só agenda; a regra fica no
 * ExpirarPedidosDesconexaoUseCase. @SchedulerLock: com várias instâncias, só uma por vez (o UPDATE já é idempotente).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiradorDePedidosDesconexaoJob {

    private final ExpirarPedidosDesconexaoUseCase expirarPedidosDesconexaoUseCase;

    @Scheduled(cron = "${app.jobs.expirar-pedidos-desconexao.cron:0 */5 * * * *}", zone = "${app.timezone:America/Sao_Paulo}")
    @SchedulerLock(name = "expirar-pedidos-desconexao", lockAtMostFor = "PT2M", lockAtLeastFor = "PT30S")
    public void executar() {
        expirarPedidosDesconexaoUseCase.executar();
    }
}
