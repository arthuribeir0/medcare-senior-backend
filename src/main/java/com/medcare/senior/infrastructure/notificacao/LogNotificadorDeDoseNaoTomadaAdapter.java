package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Aviso de dose não confirmada só em log, para desenvolvimento, testes e CI. LGPD: o log leva apenas IDs (dose,
 * cuidador e idoso) e o horário, nunca nome do idoso, nome do medicamento ou contato.
 *
 * <p>Ativa enquanto o FCM estiver desligado (app.fcm.enabled=false, o padrão). Com o FCM ligado, quem atende é o
 * PushNotificadorDeDoseNaoTomadaAdapter. Devolve PUSH_DESLIGADO: o aviso NÃO chegou ao cuidador, e o evento de
 * notificação fica registrado como FALHA (motivo PUSH_DESLIGADO), sem retentativa.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificadorDeDoseNaoTomadaAdapter implements NotificadorDeDoseNaoTomadaPort {

    private static final Logger log = LoggerFactory.getLogger(LogNotificadorDeDoseNaoTomadaAdapter.class);

    public LogNotificadorDeDoseNaoTomadaAdapter() {
        log.info("FCM desligado (app.fcm.enabled=false / FCM_ENABLED): avisos de dose não confirmada vão só para o log");
    }

    @Override
    public ResultadoEnvioPush notificar(DoseNaoTomadaNotificacao notificacao) {
        log.info("Dose {} do idoso {} não confirmada (horário {}); push desligado, aviso para o cuidador {} registrado só "
                + "em log", notificacao.doseId(), notificacao.idosoId(), notificacao.horarioProgramado(),
                notificacao.cuidadorId());
        return ResultadoEnvioPush.PUSH_DESLIGADO;
    }
}
