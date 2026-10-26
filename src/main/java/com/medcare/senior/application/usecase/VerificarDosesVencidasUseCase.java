package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.DoseEncerrada;
import com.medcare.senior.domain.dose.DoseParaAviso;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
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
 *   <li><b>Encerramento</b>: dose PENDENTE com horário + tolerância do cuidador + margem pós-aviso <= agora vira
 *       NAO_TOMADA, num único UPDATE atômico condicionado a status = PENDENTE (se a dose foi confirmada ao mesmo
 *       tempo, a confirmação vence). Sem push: o cuidador já foi avisado no fim da tolerância. Dose que chega aqui sem
 *       ter sido avisada (aplicação parada) é encerrada sem aviso.</li>
 *   <li><b>Aviso</b>: dose PENDENTE com horário + tolerância <= agora, ainda antes do encerramento e sem evento de
 *       notificação: cria o evento ({@code INSERT ... ON CONFLICT DO NOTHING}) e, SÓ se esta execução o criou e
 *       ganhou a primeira tentativa, envia o push. A dose CONTINUA PENDENTE: ainda pode ser confirmada (com
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
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;
    private final Clock clock;

    public VerificarDosesVencidasUseCase(DoseRepositoryPort doseRepository,
                                         EventoNotificacaoRepositoryPort eventos,
                                         EnvioDeAvisoService envioDeAviso,
                                         PoliticaDeJanela politicaDeJanela,
                                         Parametros parametros,
                                         CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                         Clock clock) {
        this.doseRepository = doseRepository;
        this.eventos = eventos;
        this.envioDeAviso = envioDeAviso;
        this.politicaDeJanela = politicaDeJanela;
        this.parametros = parametros;
        this.cacheDeRelatorio = cacheDeRelatorio;
        this.clock = clock;
    }

    public Resultado executar() {
        LocalDateTime agora = LocalDateTime.now(clock);

        Contagem contagem = new Contagem();
        encerrar(agora, contagem);
        avisar(agora, contagem);

        if (contagem.encerradas + contagem.avisos + contagem.falhas > 0) {
            log.info("Verificação de doses: {} encerrada(s) como NAO_TOMADA, {} aviso(s) criado(s), {} enviado(s), "
                    + "{} falha(s)", contagem.encerradas, contagem.avisos, contagem.enviados, contagem.falhas);
        }
        return new Resultado(contagem.encerradas, contagem.avisos, contagem.enviados, contagem.falhas);
    }

    private void encerrar(LocalDateTime agora, Contagem contagem) {
        try {
            // Um UPDATE só, condicionado a PENDENTE: dose confirmada antes não entra na contagem
            List<DoseEncerrada> encerradas = doseRepository.encerrarVencidas(agora, politicaDeJanela.toleranciaPadrao(),
                    politicaDeJanela.margemPosAviso(), parametros.loteMaximo());
            contagem.encerradas = encerradas.size();
            // Os relatórios diários desses pacientes mostravam as doses como pendentes
            encerradas.stream().map(DoseEncerrada::pacienteId).distinct().forEach(cacheDeRelatorio::descartarDoPaciente);
        } catch (RuntimeException e) {
            contagem.falhas++;
            log.error("Falha ao encerrar as doses vencidas; seguindo para os avisos", e);
        }
    }

    private void avisar(LocalDateTime agora, Contagem contagem) {
        // A busca usa a tolerância do cuidador dono de cada paciente (ou a padrão). Mudar a tolerância só afeta avisos
        // ainda não enviados: dose que já tem evento de notificação não é avisada de novo
        List<DoseParaAviso> doses = doseRepository.findPendentesParaAviso(agora, politicaDeJanela.toleranciaPadrao(),
                politicaDeJanela.margemPosAviso(), parametros.loteMaximo());
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
                        dose.doseId(), dose.cuidadorId(), dose.pacienteId(), dose.nomePaciente(), dose.horarioProgramado()));
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
     * @param enviados   avisos aceitos pelo provedor (com o FCM desligado, nenhum: o evento fica FALHA PUSH_DESLIGADO)
     * @param falhas     erros inesperados em alguma dose (as demais seguiram)
     */
    public record Resultado(int encerradas, int avisos, int enviados, int falhas) {
    }
}
