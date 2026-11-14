package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.NotificadorDeLembreteDoPacientePort;
import com.medcare.senior.domain.notificacao.OpcoesDeEntrega;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

/**
 * Lembrete de dose por push ao aparelho do paciente (reforço do lembrete agendado no próprio aparelho). Ativo só com
 * app.fcm.enabled=true; desligado, vale o LogNotificadorDeLembreteDoPacienteAdapter.
 *
 * <ul>
 *   <li>Paciente sem token de dispositivo (ou que não existe mais): SEM_TOKEN, sem envio.</li>
 *   <li>Token inválido: o token é apagado do paciente (só se ainda for o mesmo). O app registra um novo ao abrir.</li>
 *   <li>Falha temporária: devolvida para o evento continuar PENDENTE e a retentativa tentar de novo.</li>
 * </ul>
 * Nunca lança exceção por falha de entrega. Logs só com IDs: nem token, nem texto, nem nome do remédio.
 *
 * <p>Apresentação no Android: canal "lembrete-dose" (o mesmo dos lembretes locais, criado pelo app), prioridade máxima e
 * a tag {@code dose-{id}-1}, a mesma do primeiro lembrete local da dose: se o lembrete do aparelho já está na tela, este
 * o substitui em vez de empilhar outro igual. Validade até o aviso ao cuidador: com o aparelho sem rede, o FCM descarta
 * o lembrete que perdeu o sentido.
 *
 * <p>Conteúdo (LGPD): "Hora do remédio" e "Tomar Losartana às 08:00". O aparelho é do paciente, e o nome do remédio é
 * o que ele precisa para tomar a dose; nada além disso (dosagem, observações) vai junto. O payload de dados leva só o
 * tipo, IDs e a data.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class PushNotificadorDeLembreteDoPacienteAdapter implements NotificadorDeLembreteDoPacientePort {

    static final String TITULO = "Hora do remédio";
    static final String TIPO = "LEMBRETE_DOSE";

    private static final Logger log = LoggerFactory.getLogger(PushNotificadorDeLembreteDoPacienteAdapter.class);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("dd/MM");
    private static final Duration VALIDADE_MINIMA = Duration.ofMinutes(1);

    private final PacienteRepositoryPort pacienteRepository;
    private final NotificacaoPushPort notificacaoPush;
    private final Clock clock;

    public PushNotificadorDeLembreteDoPacienteAdapter(PacienteRepositoryPort pacienteRepository,
                                                      NotificacaoPushPort notificacaoPush,
                                                      Clock clock) {
        this.pacienteRepository = pacienteRepository;
        this.notificacaoPush = notificacaoPush;
        this.clock = clock;
    }

    @Override
    public ResultadoEnvioPush notificar(LembreteDoPacienteNotificacao notificacao) {
        Optional<String> token = pacienteRepository.findTokenFcm(notificacao.pacienteId())
                .filter(valor -> !valor.isBlank());
        if (token.isEmpty()) {
            log.info("Paciente {} sem token de dispositivo; lembrete da dose {} não enviado",
                    notificacao.pacienteId(), notificacao.doseId());
            return ResultadoEnvioPush.SEM_TOKEN;
        }

        ResultadoEnvioPush resultado = notificacaoPush.enviar(token.get(), TITULO, corpo(notificacao), dados(notificacao),
                opcoes(notificacao));
        switch (resultado) {
            case ENVIADO -> log.info("Lembrete da dose {} enviado ao paciente {}",
                    notificacao.doseId(), notificacao.pacienteId());
            case TOKEN_INVALIDO -> {
                log.warn("Token de dispositivo do paciente {} é inválido; lembrete da dose {} não entregue e token "
                        + "descartado", notificacao.pacienteId(), notificacao.doseId());
                pacienteRepository.removerTokenFcmSeIgual(notificacao.pacienteId(), token.get());
            }
            case FALHA_TEMPORARIA -> log.warn("Falha temporária ao lembrar o paciente {} da dose {}; fica para a "
                    + "retentativa", notificacao.pacienteId(), notificacao.doseId());
            case SEM_TOKEN -> log.info("Paciente {} sem token de dispositivo; lembrete da dose {} não enviado",
                    notificacao.pacienteId(), notificacao.doseId());
            case PUSH_DESLIGADO -> log.warn("Push desligado; lembrete da dose {} ao paciente {} não enviado",
                    notificacao.doseId(), notificacao.pacienteId());
        }
        return resultado;
    }

    /**
     * "Tomar Losartana às 08:00". Se a dose não é de hoje (ex.: a das 23:58, lembrada depois da meia-noite), a data
     * entra junto: "às 23:58 de 15/10". Os horários já estão no fuso da aplicação.
     */
    String corpo(LembreteDoPacienteNotificacao notificacao) {
        LocalDateTime horario = notificacao.horarioProgramado();
        String quando = "às " + HORA.format(horario);
        if (!horario.toLocalDate().equals(LocalDate.now(clock))) {
            quando += " de " + DIA.format(horario);
        }
        return "Tomar " + notificacao.nomeMedicamento() + " " + quando;
    }

    /** Tipo, doseId, pacienteId e data (yyyy-MM-dd): o app abre a dose ao tocar. Tudo como texto, como o FCM exige. */
    static Map<String, String> dados(LembreteDoPacienteNotificacao notificacao) {
        return Map.of(
                "tipo", TIPO,
                "doseId", String.valueOf(notificacao.doseId()),
                "pacienteId", String.valueOf(notificacao.pacienteId()),
                "data", notificacao.horarioProgramado().toLocalDate().toString());
    }

    /** A tag do primeiro lembrete local da dose (o app usa "dose-{id}-1"): substitui em vez de empilhar. */
    static String tag(Long doseId) {
        return "dose-" + doseId + "-1";
    }

    /** Canal do lembrete, tag da dose, prioridade máxima e validade até o aviso ao cuidador (no mínimo 1 minuto). */
    OpcoesDeEntrega opcoes(LembreteDoPacienteNotificacao notificacao) {
        Duration restante = Duration.between(LocalDateTime.now(clock), notificacao.validoAte());
        Duration validade = restante.compareTo(VALIDADE_MINIMA) < 0 ? VALIDADE_MINIMA : restante;
        return new OpcoesDeEntrega(OpcoesDeEntrega.CANAL_LEMBRETE_DOSE, tag(notificacao.doseId()), true, validade);
    }
}
