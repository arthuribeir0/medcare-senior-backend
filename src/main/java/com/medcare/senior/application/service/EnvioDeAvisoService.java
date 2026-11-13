package com.medcare.senior.application.service;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.NotificadorDeLembreteDoPacientePort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * Uma tentativa de entrega de um evento de notificação JÁ REIVINDICADO (quem chama ganhou a tentativa em
 * {@link EventoNotificacaoRepositoryPort#reivindicar}), e o registro do resultado no evento. Vale para os dois tipos:
 * o aviso ao cuidador ({@link #enviar}) e o lembrete ao paciente ({@link #enviarLembrete}).
 *
 * <ul>
 *   <li>ENVIADO -> evento ENVIADO.</li>
 *   <li>FALHA_TEMPORARIA (ou exceção do notificador) -> evento continua PENDENTE, para a retentativa.</li>
 *   <li>TOKEN_INVALIDO -> evento FALHA (TOKEN_INVALIDO). O notificador já descartou o token do destinatário.</li>
 *   <li>SEM_TOKEN -> evento FALHA (SEM_TOKEN).</li>
 *   <li>PUSH_DESLIGADO -> evento FALHA (PUSH_DESLIGADO), sem retentativa: com o FCM desligado o push não chega.</li>
 * </ul>
 * Usado pela primeira tentativa (varredura de doses e job de lembrete) e pela retentativa. Nunca lança exceção por falha
 * de entrega.
 */
@Service
public class EnvioDeAvisoService {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeAvisoService.class);

    private final NotificadorDeDoseNaoTomadaPort notificador;
    private final NotificadorDeLembreteDoPacientePort notificadorDoPaciente;
    private final EventoNotificacaoRepositoryPort eventos;

    public EnvioDeAvisoService(NotificadorDeDoseNaoTomadaPort notificador,
                               NotificadorDeLembreteDoPacientePort notificadorDoPaciente,
                               EventoNotificacaoRepositoryPort eventos) {
        this.notificador = notificador;
        this.notificadorDoPaciente = notificadorDoPaciente;
        this.eventos = eventos;
    }

    /** Aviso ao cuidador de dose não confirmada. */
    public ResultadoEnvioPush enviar(Long eventoId, DoseNaoTomadaNotificacao notificacao) {
        return registrar(eventoId, notificarSemFalhar(eventoId, notificacao.doseId(),
                () -> notificador.notificar(notificacao)));
    }

    /** Lembrete da dose ao aparelho do paciente. */
    public ResultadoEnvioPush enviarLembrete(Long eventoId, LembreteDoPacienteNotificacao notificacao) {
        return registrar(eventoId, notificarSemFalhar(eventoId, notificacao.doseId(),
                () -> notificadorDoPaciente.notificar(notificacao)));
    }

    private ResultadoEnvioPush registrar(Long eventoId, ResultadoEnvioPush resultado) {
        switch (resultado) {
            case ENVIADO -> eventos.marcarEnviado(eventoId);
            case TOKEN_INVALIDO -> eventos.marcarFalha(eventoId, MotivoFalhaNotificacao.TOKEN_INVALIDO);
            case SEM_TOKEN -> eventos.marcarFalha(eventoId, MotivoFalhaNotificacao.SEM_TOKEN);
            case PUSH_DESLIGADO -> eventos.marcarFalha(eventoId, MotivoFalhaNotificacao.PUSH_DESLIGADO);
            case FALHA_TEMPORARIA -> {
                // Continua PENDENTE com a tentativa contada: a retentativa decide quando tentar de novo
            }
        }
        return resultado;
    }

    /** Nunca nulo: exceção ou resposta vazia do notificador viram falha temporária (a retentativa tenta de novo). */
    private ResultadoEnvioPush notificarSemFalhar(Long eventoId, Long doseId, Supplier<ResultadoEnvioPush> envio) {
        try {
            ResultadoEnvioPush resultado = envio.get();
            return resultado == null ? ResultadoEnvioPush.FALHA_TEMPORARIA : resultado;
        } catch (RuntimeException e) {
            log.error("Erro inesperado ao notificar a dose {} (evento {}); tratado como falha temporária",
                    doseId, eventoId, e);
            return ResultadoEnvioPush.FALHA_TEMPORARIA;
        }
    }
}
