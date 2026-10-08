package com.medcare.senior.application.service;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
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
 * min(hoje + {@value #JANELA_DIAS} dias, dataFim), nos dias da semana e horários do tratamento. O job diário
 * (ReabastecerDosesUseCase) empurra a janela para frente.
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

    public static final int JANELA_DIAS = 30;

    private final DoseRepositoryPort doseRepository;
    private final Clock clock;

    public GeradorDeDosesService(DoseRepositoryPort doseRepository, Clock clock) {
        this.doseRepository = doseRepository;
        this.clock = clock;
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
        LocalDate fim = hoje.plusDays(JANELA_DIAS);
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
