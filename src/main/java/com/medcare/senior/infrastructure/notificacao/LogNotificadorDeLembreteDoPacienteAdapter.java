package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeLembreteDoPacientePort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Lembrete ao paciente só em log, para desenvolvimento, testes, CI e instalações sem a credencial do Firebase. LGPD: o
 * log leva apenas IDs (dose e paciente) e o horário, nunca o nome do remédio nem o token.
 *
 * <p>Ativo enquanto o FCM estiver desligado (app.fcm.enabled=false, o padrão). Devolve PUSH_DESLIGADO: o lembrete NÃO
 * chegou ao aparelho, e o evento fica FALHA (motivo PUSH_DESLIGADO), sem retentativa. O lembrete agendado no próprio
 * aparelho continua valendo.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificadorDeLembreteDoPacienteAdapter implements NotificadorDeLembreteDoPacientePort {

    private static final Logger log = LoggerFactory.getLogger(LogNotificadorDeLembreteDoPacienteAdapter.class);

    @Override
    public ResultadoEnvioPush notificar(LembreteDoPacienteNotificacao notificacao) {
        log.info("Lembrete da dose {} (horário {}) ao paciente {}: push desligado, registrado só em log",
                notificacao.doseId(), notificacao.horarioProgramado(), notificacao.pacienteId());
        return ResultadoEnvioPush.PUSH_DESLIGADO;
    }
}
