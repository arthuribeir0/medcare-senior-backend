package com.medcare.senior.application.service;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Uma tentativa de entrega do aviso de um evento JÁ REIVINDICADO (quem chama ganhou a tentativa em
 * {@link EventoNotificacaoRepositoryPort#reivindicar}), e o registro do resultado no evento:
 *
 * <ul>
 *   <li>ENVIADO -> evento ENVIADO.</li>
 *   <li>FALHA_TEMPORARIA (ou exceção do notificador) -> evento continua PENDENTE, para a retentativa.</li>
 *   <li>TOKEN_INVALIDO -> evento FALHA (TOKEN_INVALIDO). O notificador já descartou o token do cuidador.</li>
 *   <li>SEM_TOKEN -> evento FALHA (SEM_TOKEN).</li>
 *   <li>PUSH_DESLIGADO -> evento FALHA (PUSH_DESLIGADO), sem retentativa: com o FCM desligado o aviso não chega.</li>
 * </ul>
 * Usado pelo aviso (primeira tentativa) e pela retentativa. Nunca lança exceção por falha de entrega.
 */
@Service
public class EnvioDeAvisoService {

    private static final Logger log = LoggerFactory.getLogger(EnvioDeAvisoService.class);

    private final NotificadorDeDoseNaoTomadaPort notificador;
    private final EventoNotificacaoRepositoryPort eventos;

    public EnvioDeAvisoService(NotificadorDeDoseNaoTomadaPort notificador, EventoNotificacaoRepositoryPort eventos) {
        this.notificador = notificador;
        this.eventos = eventos;
    }

    public ResultadoEnvioPush enviar(Long eventoId, DoseNaoTomadaNotificacao notificacao) {
        ResultadoEnvioPush resultado;
        try {
            resultado = notificador.notificar(notificacao);
        } catch (RuntimeException e) {
            log.error("Erro inesperado ao avisar a dose {} (evento {}); tratado como falha temporária",
                    notificacao.doseId(), eventoId, e);
            resultado = ResultadoEnvioPush.FALHA_TEMPORARIA;
        }
        if (resultado == null) {
            resultado = ResultadoEnvioPush.FALHA_TEMPORARIA;
        }

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
}
