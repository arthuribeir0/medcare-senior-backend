package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.DoseVencida;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Varredura de doses vencidas: dose PENDENTE cujo horário programado + tolerância já passou vira NAO_TOMADA, e o
 * cuidador é avisado.
 *
 * <ul>
 *   <li>Busca no máximo {@code loteMaximo} doses por execução, das mais antigas para as mais novas; o restante fica
 *       para a próxima execução.</li>
 *   <li>A gravação é um UPDATE atômico condicionado a status = PENDENTE: se o idoso confirmou a dose ao mesmo tempo,
 *       a confirmação vence, nada muda e ninguém é avisado.</li>
 *   <li>Só avisa se a dose venceu (horário + tolerância) há menos de {@code janelaNotificacao}. Doses muito antigas
 *       (ex.: aplicação parada por um dia) são marcadas, mas não geram aviso atrasado.</li>
 *   <li>Doses anonimizadas e de tratamento ou medicamento removido nem chegam aqui (a busca as ignora).</li>
 * </ul>
 *
 * <p>NÃO é @Transactional de propósito: cada UPDATE roda na própria transação, para que o aviso só saia depois que a
 * mudança foi gravada e um erro em uma dose não desfaça nem interrompa as demais.
 */
@Service
public class VerificarDosesVencidasUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerificarDosesVencidasUseCase.class);

    private final DoseRepositoryPort doseRepository;
    private final NotificadorDeDoseNaoTomadaPort notificador;
    private final Parametros parametros;
    private final Clock clock;

    public VerificarDosesVencidasUseCase(DoseRepositoryPort doseRepository,
                                         NotificadorDeDoseNaoTomadaPort notificador,
                                         Parametros parametros,
                                         Clock clock) {
        this.doseRepository = doseRepository;
        this.notificador = notificador;
        this.parametros = parametros;
        this.clock = clock;
    }

    public Resultado executar() {
        LocalDateTime agora = LocalDateTime.now(clock);
        // Vencida = horário programado + tolerância <= agora, ou seja, horário programado <= agora - tolerância
        LocalDateTime vencidasAte = agora.minus(parametros.tolerancia());
        // Venceu há menos da janela = horário programado + tolerância > agora - janela
        LocalDateTime notificarVencidasDepoisDe = agora.minus(parametros.janelaNotificacao());

        List<DoseVencida> vencidas = doseRepository.findPendentesVencidas(vencidasAte, parametros.loteMaximo());

        int marcadas = 0;
        int notificadas = 0;
        int falhas = 0;
        for (DoseVencida vencida : vencidas) {
            try {
                if (doseRepository.registrarSePendente(vencida.dose().marcarComoNaoTomada()) == 0) {
                    // Perdeu a corrida: a dose foi registrada (ex.: confirmada) entre a busca e o UPDATE
                    continue;
                }
                marcadas++;

                LocalDateTime vencimento = vencida.horarioProgramado().plus(parametros.tolerancia());
                if (vencimento.isAfter(notificarVencidasDepoisDe)) {
                    notificador.notificar(new DoseNaoTomadaNotificacao(vencida.doseId(), vencida.cuidadorId(),
                            vencida.nomeIdoso(), vencida.nomeMedicamento(), vencida.horarioProgramado()));
                    notificadas++;
                }
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha ao verificar a dose {}; seguindo para as demais", vencida.doseId(), e);
            }
        }

        if (!vencidas.isEmpty()) {
            log.info("Verificação de doses: {} vencida(s) encontrada(s), {} marcada(s) como NAO_TOMADA, "
                    + "{} aviso(s) enviado(s), {} falha(s)", vencidas.size(), marcadas, notificadas, falhas);
        }
        return new Resultado(vencidas.size(), marcadas, notificadas, falhas);
    }

    /**
     * @param tolerancia        quanto tempo depois do horário programado a dose ainda pode ser confirmada
     * @param janelaNotificacao até quanto tempo depois de vencer a dose ainda gera aviso ao cuidador
     * @param loteMaximo        máximo de doses tratadas por execução
     */
    public record Parametros(Duration tolerancia, Duration janelaNotificacao, int loteMaximo) {

        public Parametros {
            if (tolerancia == null || tolerancia.isNegative()) {
                throw new IllegalArgumentException("A tolerância deve ser zero ou positiva");
            }
            if (janelaNotificacao == null || janelaNotificacao.isNegative() || janelaNotificacao.isZero()) {
                throw new IllegalArgumentException("A janela de notificação deve ser positiva");
            }
            if (loteMaximo < 1) {
                throw new IllegalArgumentException("O lote máximo deve ser de pelo menos 1 dose");
            }
        }
    }

    public record Resultado(int vencidas, int marcadas, int notificadas, int falhas) {
    }
}
