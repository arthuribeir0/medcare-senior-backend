package com.medcare.senior.domain.tratamento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Período e horários em que um medicamento deve ser tomado. A posse segue o caminho
 * tratamento -> medicamento -> idoso -> cuidador.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tratamento {

    private Long id;
    private Long medicamentoId;
    private LocalDate dataInicio;

    /**
     * {@code null} = tratamento contínuo, sem data de término.
     */
    private LocalDate dataFim;

    private List<LocalTime> horarios;
    private List<DayOfWeek> diasSemana;

    /**
     * Remoção lógica: preenchido quando o medicamento do tratamento é removido. Tratamento removido não gera doses
     * e é invisível na API, mas continua no banco para preservar o histórico de doses (RN-008).
     */
    private LocalDateTime removidoEm;

    /**
     * RN-006: o tratamento está ativo se não foi removido e não tem data de término, ou ela é hoje ou depois.
     */
    public boolean isAtivo(LocalDate hoje) {
        return !isRemovido() && (dataFim == null || !dataFim.isBefore(hoje));
    }

    public boolean isRemovido() {
        return removidoEm != null;
    }

    /**
     * RN-007: ao menos um horário, sem horários repetidos; ao menos um dia da semana; e data de término
     * (quando houver) igual ou posterior à data de início.
     */
    public void validar() {
        if (horarios == null || horarios.isEmpty()) {
            throw new HorariosObrigatoriosException();
        }

        Set<LocalTime> vistos = new HashSet<>();
        for (LocalTime horario : horarios) {
            if (!vistos.add(horario)) {
                throw new HorariosDuplicadosException(horario);
            }
        }

        if (diasSemana == null || diasSemana.isEmpty()) {
            throw new DiasSemanaObrigatoriosException();
        }

        if (dataFim != null && dataFim.isBefore(dataInicio)) {
            throw new PeriodoTratamentoInvalidoException();
        }
    }
}
