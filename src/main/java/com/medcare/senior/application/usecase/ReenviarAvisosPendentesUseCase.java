package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoParaRetentativa;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Retentativa dos avisos de dose não confirmada que falharam temporariamente (RN-010), com intervalo crescente.
 * Para cada evento PENDENTE elegível:
 *
 * <ol>
 *   <li>tentativas esgotadas -> FALHA (TENTATIVAS_ESGOTADAS);</li>
 *   <li>dose que não está mais PENDENTE (confirmada, com atraso ou encerrada) ou sem dono -> FALHA
 *       (DOSE_JA_REGISTRADA), sem push;</li>
 *   <li>senão, reivindica a próxima tentativa (UPDATE condicionado às tentativas lidas: só um job envia), relê a dose
 *       e envia pelo {@link EnvioDeAvisoService} (ENVIADO, TOKEN_INVALIDO e SEM_TOKEN encerram o evento; falha
 *       temporária na última tentativa -> TENTATIVAS_ESGOTADAS).</li>
 * </ol>
 * NÃO é @Transactional: cada passo grava na própria transação, e o erro de um evento não interrompe os demais.
 */
@Service
public class ReenviarAvisosPendentesUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReenviarAvisosPendentesUseCase.class);

    private final EventoNotificacaoRepositoryPort eventos;
    private final DoseRepositoryPort doseRepository;
    private final EnvioDeAvisoService envioDeAviso;
    private final Parametros parametros;
    private final Clock clock;

    public ReenviarAvisosPendentesUseCase(EventoNotificacaoRepositoryPort eventos,
                                          DoseRepositoryPort doseRepository,
                                          EnvioDeAvisoService envioDeAviso,
                                          Parametros parametros,
                                          Clock clock) {
        this.eventos = eventos;
        this.doseRepository = doseRepository;
        this.envioDeAviso = envioDeAviso;
        this.parametros = parametros;
        this.clock = clock;
    }

    public Resultado executar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        Contagem contagem = new Contagem();
        for (EventoParaRetentativa evento : eventos.findParaRetentativa(agora, parametros.maxTentativas(),
                parametros.intervaloBase(), parametros.loteMaximo())) {
            try {
                processar(evento, agora, contagem);
            } catch (RuntimeException e) {
                contagem.erros++;
                log.error("Falha ao reprocessar o aviso {} da dose {}; seguindo para os demais",
                        evento.eventoId(), evento.doseId(), e);
            }
        }
        if (contagem.tentativas + contagem.canceladas + contagem.esgotadas + contagem.erros > 0) {
            log.info("Retentativa de avisos: {} tentativa(s), {} enviada(s), {} cancelada(s) (dose já registrada), "
                            + "{} esgotada(s), {} erro(s)", contagem.tentativas, contagem.enviadas, contagem.canceladas,
                    contagem.esgotadas, contagem.erros);
        }
        return new Resultado(contagem.tentativas, contagem.enviadas, contagem.canceladas, contagem.esgotadas,
                contagem.erros);
    }

    private void processar(EventoParaRetentativa evento, LocalDateTime agora, Contagem contagem) {
        if (evento.tentativas() >= parametros.maxTentativas()) {
            // Ex.: a instância caiu depois de reivindicar a última tentativa e antes de registrar o resultado
            eventos.marcarFalha(evento.eventoId(), MotivoFalhaNotificacao.TENTATIVAS_ESGOTADAS);
            contagem.esgotadas++;
            return;
        }
        if (evento.semDono() || evento.statusDose() != StatusDose.PENDENTE) {
            eventos.marcarFalha(evento.eventoId(), MotivoFalhaNotificacao.DOSE_JA_REGISTRADA);
            contagem.canceladas++;
            return;
        }
        if (!eventos.reivindicar(evento.eventoId(), evento.tentativas(), agora)) {
            // Outro job (o do aviso ou outra instância) pegou esta tentativa: quem reivindicou envia
            return;
        }
        // Releitura depois da reivindicação: o idoso pode ter confirmado entre a busca e agora
        Optional<StatusDose> statusAtual = doseRepository.findStatusById(evento.doseId());
        if (statusAtual.filter(StatusDose.PENDENTE::equals).isEmpty()) {
            eventos.marcarFalha(evento.eventoId(), MotivoFalhaNotificacao.DOSE_JA_REGISTRADA);
            contagem.canceladas++;
            return;
        }

        contagem.tentativas++;
        ResultadoEnvioPush resultado = envioDeAviso.enviar(evento.eventoId(), new DoseNaoTomadaNotificacao(
                evento.doseId(), evento.cuidadorId(), evento.idosoId(), evento.nomeIdoso(), evento.horarioProgramado()));
        if (resultado == ResultadoEnvioPush.ENVIADO) {
            contagem.enviadas++;
        } else if (resultado == ResultadoEnvioPush.FALHA_TEMPORARIA
                && evento.tentativas() + 1 >= parametros.maxTentativas()) {
            eventos.marcarFalha(evento.eventoId(), MotivoFalhaNotificacao.TENTATIVAS_ESGOTADAS);
            contagem.esgotadas++;
        }
    }

    private static final class Contagem {
        private int tentativas;
        private int enviadas;
        private int canceladas;
        private int esgotadas;
        private int erros;
    }

    /**
     * @param maxTentativas total de tentativas por aviso, contando a primeira (feita pela varredura de doses)
     * @param intervaloBase espera depois da 1ª tentativa; dobra a cada nova (5 min -> 5, 10, 20, 40)
     * @param loteMaximo    máximo de eventos tratados por execução
     */
    public record Parametros(int maxTentativas, Duration intervaloBase, int loteMaximo) {

        public Parametros {
            if (maxTentativas < 1) {
                throw new IllegalArgumentException("O aviso deve ter pelo menos 1 tentativa");
            }
            if (intervaloBase == null || intervaloBase.toMinutes() < 1) {
                throw new IllegalArgumentException("O intervalo base da retentativa deve ser de pelo menos 1 minuto");
            }
            if (loteMaximo < 1) {
                throw new IllegalArgumentException("O lote máximo deve ser de pelo menos 1 evento");
            }
        }
    }

    /**
     * @param tentativas nova tentativa de envio feita nesta execução
     * @param enviadas   aceitas pelo provedor
     * @param canceladas eventos encerrados porque a dose deixou de estar PENDENTE (ou ficou sem dono)
     * @param esgotadas  eventos encerrados por esgotar as tentativas
     * @param erros      erros inesperados em algum evento (os demais seguiram)
     */
    public record Resultado(int tentativas, int enviadas, int canceladas, int esgotadas, int erros) {
    }
}
