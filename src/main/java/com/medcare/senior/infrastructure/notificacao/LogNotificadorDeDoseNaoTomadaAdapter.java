package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementação inicial do aviso de dose não tomada: só registra em log. LGPD: o log leva apenas IDs (dose e
 * cuidador) e o horário, nunca nome do idoso, nome do medicamento ou contato.
 *
 * <p>Ativa enquanto o FCM estiver desligado (app.fcm.enabled=false, o padrão). Com o FCM ligado, quem atende é o
 * PushNotificadorDeDoseNaoTomadaAdapter.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificadorDeDoseNaoTomadaAdapter implements NotificadorDeDoseNaoTomadaPort {

    private static final Logger log = LoggerFactory.getLogger(LogNotificadorDeDoseNaoTomadaAdapter.class);

    public LogNotificadorDeDoseNaoTomadaAdapter() {
        log.info("FCM desligado (app.fcm.enabled=false / FCM_ENABLED): avisos de dose não tomada vão só para o log");
    }

    @Override
    public void notificar(DoseNaoTomadaNotificacao notificacao) {
        log.info("Dose {} não confirmada (horário {}); aviso para o cuidador {} registrado só em log",
                notificacao.doseId(), notificacao.horarioProgramado(), notificacao.cuidadorId());
    }
}
