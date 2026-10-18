package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Aviso de dose não confirmada por push (UC-007). Ativo só com app.fcm.enabled=true; desligado, vale o
 * LogNotificadorDeDoseNaoTomadaAdapter.
 *
 * <ul>
 *   <li>Cuidador sem token de dispositivo (ou conta já excluída): SEM_TOKEN, sem envio.</li>
 *   <li>Token inválido: o token é apagado do cuidador (só se ainda for o mesmo), para não tentar de novo um aparelho
 *       que não existe mais. O app registra um token novo no próximo login.</li>
 *   <li>Falha temporária: devolvida para o evento continuar PENDENTE e a retentativa tentar de novo.</li>
 * </ul>
 * Nunca lança exceção por falha de entrega. Logs só com IDs, sem nome de idoso, medicamento ou token.
 *
 * <p>Conteúdo (LGPD): o texto leva o nome do idoso e o horário, mas NUNCA o medicamento (dado de saúde que passaria
 * pelos servidores do Google). O payload de dados leva só o tipo, IDs e a data, para o app abrir a dose.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class PushNotificadorDeDoseNaoTomadaAdapter implements NotificadorDeDoseNaoTomadaPort {

    static final String TITULO = "Dose não confirmada";
    static final String TIPO = "DOSE_NAO_CONFIRMADA";

    private static final Logger log = LoggerFactory.getLogger(PushNotificadorDeDoseNaoTomadaAdapter.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");

    private final CuidadorRepositoryPort cuidadorRepository;
    private final NotificacaoPushPort notificacaoPush;
    private final Clock clock;

    public PushNotificadorDeDoseNaoTomadaAdapter(CuidadorRepositoryPort cuidadorRepository,
                                                 NotificacaoPushPort notificacaoPush,
                                                 Clock clock) {
        this.cuidadorRepository = cuidadorRepository;
        this.notificacaoPush = notificacaoPush;
        this.clock = clock;
    }

    @Override
    public ResultadoEnvioPush notificar(DoseNaoTomadaNotificacao notificacao) {
        Optional<String> token = cuidadorRepository.findById(notificacao.cuidadorId())
                .map(Cuidador::getFcmToken)
                .filter(valor -> !valor.isBlank());
        if (token.isEmpty()) {
            log.info("Cuidador {} sem token de dispositivo; aviso da dose {} não enviado",
                    notificacao.cuidadorId(), notificacao.doseId());
            return ResultadoEnvioPush.SEM_TOKEN;
        }

        ResultadoEnvioPush resultado = notificacaoPush.enviar(token.get(), TITULO, corpo(notificacao), dados(notificacao));
        switch (resultado) {
            case ENVIADO -> log.info("Aviso da dose {} enviado ao cuidador {}",
                    notificacao.doseId(), notificacao.cuidadorId());
            case TOKEN_INVALIDO -> {
                log.warn("Token de dispositivo do cuidador {} é inválido; aviso da dose {} não entregue e token "
                        + "descartado", notificacao.cuidadorId(), notificacao.doseId());
                cuidadorRepository.removerFcmTokenSeIgual(notificacao.cuidadorId(), token.get());
            }
            case FALHA_TEMPORARIA -> log.warn("Falha temporária ao avisar o cuidador {} da dose {}; fica para a "
                    + "retentativa", notificacao.cuidadorId(), notificacao.doseId());
            case SEM_TOKEN -> log.info("Cuidador {} sem token de dispositivo; aviso da dose {} não enviado",
                    notificacao.cuidadorId(), notificacao.doseId());
        }
        return resultado;
    }

    /**
     * "Maria ainda não confirmou a dose das 08:00". Se a dose não é de hoje (ex.: venceu de madrugada), a data entra
     * junto para o horário não ser confundido: "das 22:00 de 15/10". Os horários já estão no fuso da aplicação.
     */
    String corpo(DoseNaoTomadaNotificacao notificacao) {
        LocalDateTime horario = notificacao.horarioProgramado();
        String quando = "das " + HORA.format(horario);
        if (!horario.toLocalDate().equals(LocalDate.now(clock))) {
            quando += " de " + DIA.format(horario);
        }
        return notificacao.nomeIdoso() + " ainda não confirmou a dose " + quando;
    }

    /**
     * Payload para o app abrir a dose ao tocar na notificação: tipo, doseId, idosoId e data (yyyy-MM-dd, no fuso da
     * aplicação). Tudo como texto, como o FCM exige.
     */
    static Map<String, String> dados(DoseNaoTomadaNotificacao notificacao) {
        return Map.of(
                "tipo", TIPO,
                "doseId", String.valueOf(notificacao.doseId()),
                "idosoId", String.valueOf(notificacao.idosoId()),
                "data", notificacao.horarioProgramado().toLocalDate().toString());
    }
}
