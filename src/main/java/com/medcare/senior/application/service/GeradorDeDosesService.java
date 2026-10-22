package com.medcare.senior.application.service;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Motor de geração de doses com janela rolante: mantém doses PENDENTES geradas de max(dataInicio, hoje) até
 * min(hoje + horizonte, dataFim), nos dias da semana e horários do tratamento. O horizonte é configurável
 * (app.doses.horizonte-dias, padrão {@value #HORIZONTE_PADRAO_DIAS} dias, de {@value #HORIZONTE_MINIMO_DIAS} a
 * {@value #HORIZONTE_MAXIMO_DIAS}). O job diário (ReabastecerDosesUseCase) empurra a janela para frente: com o
 * padrão, cada tratamento ativo tem sempre de 30 a 31 dias de doses à frente.
 *
 * <ul>
 *   <li>No dia de hoje, só gera horários posteriores ao momento atual.</li>
 *   <li>Idempotente: horários que já têm dose (de qualquer status) não são gerados de novo. O índice único
 *       (tratamento_id, horario_programado) no banco é a última garantia contra duplicatas concorrentes.</li>
 *   <li>Tratamento removido não gera doses.</li>
 * </ul>
 */
@Service
public class GeradorDeDosesService {

    public static final int HORIZONTE_PADRAO_DIAS = 30;
    public static final int HORIZONTE_MINIMO_DIAS = 1;
    public static final int HORIZONTE_MAXIMO_DIAS = 90;

    private final DoseRepositoryPort doseRepository;
    private final Clock clock;
    private final int horizonteDias;

    /**
     * @param horizonteDias quantos dias à frente de hoje a janela vai
     * @throws IllegalArgumentException fora de [1, 90] (a aplicação não sobe com o valor inválido)
     */
    public GeradorDeDosesService(DoseRepositoryPort doseRepository, Clock clock,
                                 @Value("${app.doses.horizonte-dias:" + HORIZONTE_PADRAO_DIAS + "}") int horizonteDias) {
        if (horizonteDias < HORIZONTE_MINIMO_DIAS || horizonteDias > HORIZONTE_MAXIMO_DIAS) {
            throw new IllegalArgumentException("O horizonte de geração de doses deve ser de " + HORIZONTE_MINIMO_DIAS
                    + " a " + HORIZONTE_MAXIMO_DIAS + " dias");
        }
        this.doseRepository = doseRepository;
        this.clock = clock;
        this.horizonteDias = horizonteDias;
    }

    public int horizonteDias() {
        return horizonteDias;
    }

    /**
     * @return quantas doses novas foram geradas
     */
    @Transactional
    public int gerar(Tratamento tratamento) {
        if (tratamento.isRemovido()) {
            return 0;
        }

        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate hoje = agora.toLocalDate();

        LocalDate inicio = tratamento.getDataInicio().isAfter(hoje) ? tratamento.getDataInicio() : hoje;
        LocalDate fim = hoje.plusDays(horizonteDias);
        if (tratamento.getDataFim() != null && tratamento.getDataFim().isBefore(fim)) {
            fim = tratamento.getDataFim();
        }
        if (inicio.isAfter(fim)) {
            return 0;
        }

        Set<LocalDateTime> existentes =
                doseRepository.findHorariosProgramadosByTratamentoIdDesde(tratamento.getId(), inicio.atStartOfDay());
        List<LocalTime> horarios = tratamento.getHorarios().stream().sorted().toList();

        List<Dose> novas = new ArrayList<>();
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            if (!tratamento.getDiasSemana().contains(dia.getDayOfWeek())) {
                continue;
            }
            for (LocalTime horario : horarios) {
                LocalDateTime horarioProgramado = dia.atTime(horario);
                if (horarioProgramado.isAfter(agora) && !existentes.contains(horarioProgramado)) {
                    novas.add(Dose.pendente(tratamento.getId(), horarioProgramado));
                }
            }
        }

        if (!novas.isEmpty()) {
            doseRepository.saveAll(novas);
        }
        return novas.size();
    }
}
