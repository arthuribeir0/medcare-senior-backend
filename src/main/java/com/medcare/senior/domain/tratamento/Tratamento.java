package com.medcare.senior.domain.tratamento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Período e horários em que um medicamento deve ser tomado. A posse segue o caminho
 * tratamento -> medicamento -> paciente -> cuidador.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tratamento {

    /** Antecedência mínima de um horário novo em relação a agora, quando a dose cairia hoje. */
    public static final Duration ANTECEDENCIA_MINIMA = Duration.ofMinutes(5);

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

    /**
     * O dia tem dose deste tratamento: está dentro do período e é um dos dias da semana marcados.
     */
    public boolean temDoseNoDia(LocalDate dia) {
        return !dia.isBefore(dataInicio) && (dataFim == null || !dia.isAfter(dataFim))
                && diasSemana.contains(dia.getDayOfWeek());
    }

    /**
     * Se hoje tem dose deste tratamento, nenhum dos horários verificados pode cair do minuto atual (inclusive) até
     * agora + {@link #ANTECEDENCIA_MINIMA} (exclusive). Horários que já passaram hoje continuam valendo: a primeira dose
     * fica para o próximo dia válido. Na edição, só os horários novos ou alterados são verificados.
     *
     * @throws HorarioMuitoProximoException se algum horário verificado estiver nessa faixa
     */
    public void validarAntecedencia(LocalDateTime agora, Collection<LocalTime> verificados) {
        validarAntecedencia(agora, verificados, ANTECEDENCIA_MINIMA);
    }

    /**
     * Igual a {@link #validarAntecedencia(LocalDateTime, Collection)}, com a antecedência dada; zero (modo de teste)
     * aceita qualquer horário.
     */
    public void validarAntecedencia(LocalDateTime agora, Collection<LocalTime> verificados, Duration antecedencia) {
        LocalDate hoje = agora.toLocalDate();
        if (antecedencia.isZero() || !temDoseNoDia(hoje)) {
            return;
        }
        LocalDateTime minutoAtual = agora.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime limite = agora.plus(antecedencia);
        for (LocalTime horario : verificados) {
            // Pelo LocalDateTime de hoje: perto da meia-noite, um horário da madrugada é do dia seguinte e não conta
            LocalDateTime horarioDeHoje = hoje.atTime(horario);
            if (!horarioDeHoje.isBefore(minutoAtual) && horarioDeHoje.isBefore(limite)) {
                throw new HorarioMuitoProximoException();
            }
        }
    }

    /**
     * Data e hora da primeira dose depois de {@code agora} (a mesma que o gerador de doses cria), procurando de hoje até
     * o mesmo dia da semana seguinte; vazio se o tratamento termina antes disso.
     */
    public Optional<LocalDateTime> primeiraDoseDepoisDe(LocalDateTime agora) {
        LocalDate hoje = agora.toLocalDate();
        LocalDate dia = dataInicio.isAfter(hoje) ? dataInicio : hoje;
        List<LocalTime> ordenados = horarios.stream().sorted().toList();
        // Hoje e mais 7 dias: alcança o mesmo dia da semana seguinte (tratamento de um dia só por semana)
        for (int i = 0; i <= 7; i++, dia = dia.plusDays(1)) {
            if (dataFim != null && dia.isAfter(dataFim)) {
                break;
            }
            if (!temDoseNoDia(dia)) {
                continue;
            }
            for (LocalTime horario : ordenados) {
                LocalDateTime dose = dia.atTime(horario);
                if (dose.isAfter(agora)) {
                    return Optional.of(dose);
                }
            }
        }
        return Optional.empty();
    }
}
