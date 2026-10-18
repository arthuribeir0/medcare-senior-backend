package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseParaAviso;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Varredura de doses (RN-009, RN-014), em duas fases independentes:
 *
 * <ol>
 *   <li><b>Encerramento</b>: dose PENDENTE com horário + encerramento (180 min) <= agora vira NAO_TOMADA, por UPDATE
 *       atômico condicionado a status = PENDENTE (se o idoso confirmou ao mesmo tempo, a confirmação vence). Sem push:
 *       o cuidador já foi avisado no fim da tolerância. Dose que chega aqui sem ter sido avisada (aplicação parada)
 *       é encerrada sem aviso.</li>
 *   <li><b>Aviso</b>: dose PENDENTE com horário + tolerância <= agora, ainda antes do encerramento e sem evento de
 *       notificação: cria o evento ({@code INSERT ... ON CONFLICT DO NOTHING}) e, SÓ se esta execução o criou e
 *       ganhou a primeira tentativa, envia o push. A dose CONTINUA PENDENTE: o idoso ainda pode confirmar (com
 *       atraso) até o encerramento.</li>
 * </ol>
 * Cada fase trata no máximo {@code loteMaximo} doses por execução; o restante fica para a próxima.
 *
 * <p>NÃO é @Transactional de propósito: cada gravação roda na própria transação, para que o push só saia depois que o
 * evento foi gravado e um erro em uma dose não desfaça nem interrompa as demais.
 */
@Service
public class VerificarDosesVencidasUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificarDosesVencidasUseCase.class);

    private final DoseRepositoryPort doseRepository;
    private final EventoNotificacaoRepositoryPort eventos;
    private final EnvioDeAvisoService envioDeAviso;
    private final PoliticaDeJanela politicaDeJanela;
    private final Parametros parametros;
    private final Clock clock;

    public VerificarDosesVencidasUseCase(DoseRepositoryPort doseRepository,
                                         EventoNotificacaoRepositoryPort eventos,
                                         EnvioDeAvisoService envioDeAviso,
                                         PoliticaDeJanela politicaDeJanela,
                                         Parametros parametros,
                                         Clock clock) {
        this.doseRepository = doseRepository;
        this.eventos = eventos;
        this.envioDeAviso = envioDeAviso;
        this.politicaDeJanela = politicaDeJanela;
        this.parametros = parametros;
        this.clock = clock;
    }

    public Resultado executar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        // Encerrada = horário + encerramento <= agora, ou seja, horário <= agora - encerramento
        LocalDateTime encerradasAte = agora.minus(politicaDeJanela.encerramento());

        Contagem contagem = new Contagem();
        encerrar(encerradasAte, contagem);
        avisar(agora, encerradasAte, contagem);

        if (contagem.encerradas + contagem.avisos + contagem.falhas > 0) {
            log.info("Verificação de doses: {} encerrada(s) como NAO_TOMADA, {} aviso(s) criado(s), {} enviado(s), "
                    + "{} falha(s)", contagem.encerradas, contagem.avisos, contagem.enviados, contagem.falhas);
        }
        return new Resultado(contagem.encerradas, contagem.avisos, contagem.enviados, contagem.falhas);
    }

    private void encerrar(LocalDateTime encerradasAte, Contagem contagem) {
        for (Dose dose : doseRepository.findPendentesEncerradas(encerradasAte, parametros.loteMaximo())) {
            try {
                // 0 = perdeu a corrida: a dose foi confirmada entre a busca e o UPDATE
                if (doseRepository.registrarSePendente(dose.marcarComoNaoTomada()) == 1) {
                    contagem.encerradas++;
                }
            } catch (RuntimeException e) {
                contagem.falhas++;
                log.error("Falha ao encerrar a dose {}; seguindo para as demais", dose.getId(), e);
            }
        }
    }

    private void avisar(LocalDateTime agora, LocalDateTime encerradasAte, Contagem contagem) {
        // A busca usa a tolerância do cuidador dono de cada idoso (ou a padrão). Mudar a tolerância só afeta avisos
        // ainda não enviados: dose que já tem evento de notificação não é avisada de novo
        List<DoseParaAviso> doses = doseRepository.findPendentesParaAviso(agora, politicaDeJanela.toleranciaPadrao(),
                encerradasAte, parametros.loteMaximo());
        for (DoseParaAviso dose : doses) {
            try {
                Optional<Long> evento = eventos.criarSeAusente(dose.doseId());
                // Vazio = outra execução criou o evento antes; não reivindicou = a retentativa pegou a primeira
                // tentativa. Nos dois casos, quem venceu envia.
                if (evento.isEmpty() || !eventos.reivindicar(evento.get(), 0, agora)) {
                    continue;
                }
                contagem.avisos++;
                ResultadoEnvioPush resultado = envioDeAviso.enviar(evento.get(), new DoseNaoTomadaNotificacao(
                        dose.doseId(), dose.cuidadorId(), dose.idosoId(), dose.nomeIdoso(), dose.horarioProgramado()));
                if (resultado == ResultadoEnvioPush.ENVIADO) {
                    contagem.enviados++;
                }
            } catch (RuntimeException e) {
                contagem.falhas++;
                log.error("Falha ao avisar a dose {}; seguindo para as demais", dose.doseId(), e);
            }
        }
    }

    private static final class Contagem {
        private int encerradas;
        private int avisos;
        private int enviados;
        private int falhas;
    }

    /**
     * @param loteMaximo máximo de doses tratadas por fase em cada execução
     */
    public record Parametros(int loteMaximo) {

        public Parametros {
            if (loteMaximo < 1) {
                throw new IllegalArgumentException("O lote máximo deve ser de pelo menos 1 dose");
            }
        }
    }

    /**
     * @param encerradas doses que passaram a NAO_TOMADA nesta execução
     * @param avisos     eventos de notificação criados e com a primeira tentativa feita nesta execução
     * @param enviados   avisos aceitos pelo provedor (ou registrados em log, com o FCM desligado)
     * @param falhas     erros inesperados em alguma dose (as demais seguiram)
     */
    public record Resultado(int encerradas, int avisos, int enviados, int falhas) {
    }
}
