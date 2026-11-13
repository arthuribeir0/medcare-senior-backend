package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.DoseParaLembrete;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.notificacao.TipoEventoNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Reforço do lembrete da dose no aparelho do paciente (UC-006, passo 1). O lembrete principal é o que o próprio
 * aparelho agenda (funciona sem internet); este cobre o aparelho que não tocou: reiniciado, com o agendamento apagado
 * pelo fabricante ou atrasado pela economia de bateria.
 *
 * <p>Dose PENDENTE de paciente com aparelho registrado, cujo horário passou há pelo menos {@code atrasoMinimo} e que
 * ainda não chegou ao aviso ao cuidador (janela da {@link PoliticaDeJanela}): cria o evento LEMBRETE_PACIENTE
 * ({@code INSERT ... ON CONFLICT (dose_id, tipo) DO NOTHING}) e, SÓ se esta execução o criou e ganhou a primeira
 * tentativa, envia o push pelo {@link EnvioDeAvisoService}. A falha temporária fica para a retentativa, que desiste
 * quando a dose sai de PENDENTE ou chega ao aviso ao cuidador. Dose confirmada nunca recebe lembrete.
 *
 * <p>NÃO é @Transactional de propósito, como a varredura de doses: cada gravação roda na própria transação, o push só
 * sai depois que o evento foi gravado e um erro em uma dose não interrompe as demais.
 */
@Service
public class LembrarPacienteDaDoseUseCase {

    private static final Logger log = LoggerFactory.getLogger(LembrarPacienteDaDoseUseCase.class);

    private final DoseRepositoryPort doseRepository;
    private final EventoNotificacaoRepositoryPort eventos;
    private final EnvioDeAvisoService envioDeAviso;
    private final PoliticaDeJanela politicaDeJanela;
    private final Parametros parametros;
    private final Clock clock;

    public LembrarPacienteDaDoseUseCase(DoseRepositoryPort doseRepository,
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
        int lembretes = 0;
        int enviados = 0;
        int falhas = 0;
        List<DoseParaLembrete> doses = doseRepository.findPendentesParaLembrete(agora, parametros.atrasoMinimo(),
                politicaDeJanela.toleranciaPadrao(), parametros.loteMaximo());
        for (DoseParaLembrete dose : doses) {
            try {
                Duration tolerancia = politicaDeJanela.toleranciaEfetiva(dose.toleranciaMinutos());
                // A busca já filtra; a política é a fonte da verdade e decide a fronteira
                if (!politicaDeJanela.dentroDoLembreteAoPaciente(dose.horarioProgramado(), agora,
                        parametros.atrasoMinimo(), tolerancia)) {
                    continue;
                }
                Optional<Long> evento = eventos.criarSeAusente(dose.doseId(), TipoEventoNotificacao.LEMBRETE_PACIENTE);
                // Vazio = outra execução criou o evento antes; não reivindicou = a retentativa pegou a primeira
                // tentativa. Nos dois casos, quem venceu envia.
                if (evento.isEmpty() || !eventos.reivindicar(evento.get(), 0, agora)) {
                    continue;
                }
                lembretes++;
                ResultadoEnvioPush resultado = envioDeAviso.enviarLembrete(evento.get(), new LembreteDoPacienteNotificacao(
                        dose.doseId(), dose.pacienteId(), dose.nomeMedicamento(), dose.horarioProgramado(),
                        politicaDeJanela.avisoEm(dose.horarioProgramado(), tolerancia)));
                if (resultado == ResultadoEnvioPush.ENVIADO) {
                    enviados++;
                }
            } catch (RuntimeException e) {
                falhas++;
                log.error("Falha ao lembrar o paciente da dose {}; seguindo para as demais", dose.doseId(), e);
            }
        }
        if (lembretes + falhas > 0) {
            log.info("Lembrete ao paciente: {} lembrete(s) criado(s), {} enviado(s), {} falha(s)", lembretes, enviados,
                    falhas);
        }
        return new Resultado(lembretes, enviados, falhas);
    }

    /**
     * @param atrasoMinimo quanto depois do horário o servidor reforça o lembrete do aparelho (padrão: 2 minutos)
     * @param loteMaximo   máximo de doses tratadas por execução
     */
    public record Parametros(Duration atrasoMinimo, int loteMaximo) {

        public Parametros {
            if (atrasoMinimo == null || atrasoMinimo.isNegative()) {
                throw new IllegalArgumentException("O atraso do lembrete ao paciente não pode ser negativo");
            }
            if (loteMaximo < 1) {
                throw new IllegalArgumentException("O lote máximo deve ser de pelo menos 1 dose");
            }
        }
    }

    /**
     * @param lembretes eventos de lembrete criados e com a primeira tentativa feita nesta execução
     * @param enviados  lembretes aceitos pelo provedor (com o FCM desligado, nenhum: o evento fica FALHA PUSH_DESLIGADO)
     * @param falhas    erros inesperados em alguma dose (as demais seguiram)
     */
    public record Resultado(int lembretes, int enviados, int falhas) {
    }
}
