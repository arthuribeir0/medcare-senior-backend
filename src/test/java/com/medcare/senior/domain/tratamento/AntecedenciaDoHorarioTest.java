package com.medcare.senior.domain.tratamento;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Antecedência mínima de 5 minutos para um horário que cairia hoje, e a primeira dose depois de agora.
 * 08/10/2026 é uma quinta-feira.
 */
class AntecedenciaDoHorarioTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 8);
    private static final List<DayOfWeek> QUINTA = List.of(DayOfWeek.THURSDAY);

    private static Tratamento tratamento(LocalDate inicio, List<DayOfWeek> dias, LocalTime... horarios) {
        return Tratamento.builder().medicamentoId(1L).dataInicio(inicio).horarios(List.of(horarios)).diasSemana(dias).build();
    }

    private static void recusa(LocalDateTime agora, LocalTime horario) {
        Tratamento t = tratamento(HOJE, QUINTA, horario);
        assertThatThrownBy(() -> t.validarAntecedencia(agora, t.getHorarios()))
                .isInstanceOf(HorarioMuitoProximoException.class)
                .hasMessage("Não é possível cadastrar um horário que seja agora ou daqui a menos de 5 minutos. Escolha um "
                        + "horário com pelo menos 5 minutos de diferença do horário atual.");
    }

    private static void aceita(Tratamento t, LocalDateTime agora) {
        assertThatCode(() -> t.validarAntecedencia(agora, t.getHorarios())).doesNotThrowAnyException();
    }

    @Test
    void exatamenteOMinutoAtualEhRecusadoMesmoComSegundosPassados() {
        recusa(HOJE.atTime(10, 0), LocalTime.of(10, 0));
        recusa(HOJE.atTime(10, 0, 30), LocalTime.of(10, 0));
    }

    @Test
    void menosDeCincoMinutosAFrenteEhRecusado() {
        recusa(HOJE.atTime(10, 0), LocalTime.of(10, 4, 59));
        recusa(HOJE.atTime(10, 0, 1), LocalTime.of(10, 5));
        recusa(HOJE.atTime(10, 0), LocalTime.of(10, 3));
    }

    @Test
    void exatamenteCincoMinutosOuMaisEhAceito() {
        aceita(tratamento(HOJE, QUINTA, LocalTime.of(10, 5)), HOJE.atTime(10, 0));
        aceita(tratamento(HOJE, QUINTA, LocalTime.of(10, 6)), HOJE.atTime(10, 0, 59));
    }

    @Test
    void horarioQueJaPassouHojeEhAceitoEAPrimeiraDoseFicaParaOProximoDiaValido() {
        Tratamento t = tratamento(HOJE, QUINTA, LocalTime.of(9, 0));
        LocalDateTime agora = HOJE.atTime(10, 0);

        aceita(t, agora);
        assertThat(t.primeiraDoseDepoisDe(agora)).contains(HOJE.plusDays(7).atTime(9, 0));
    }

    @Test
    void primeiraDoseEhHojeQuandoAindaHaHorarioDepoisDeAgora() {
        Tratamento t = tratamento(HOJE, QUINTA, LocalTime.of(9, 0), LocalTime.of(20, 0));
        assertThat(t.primeiraDoseDepoisDe(HOJE.atTime(10, 0))).contains(HOJE.atTime(20, 0));
    }

    @Test
    void inicioAmanhaNaoEhAfetado() {
        Tratamento t = tratamento(HOJE.plusDays(1), List.of(DayOfWeek.values()), LocalTime.of(10, 2));
        aceita(t, HOJE.atTime(10, 0));
        assertThat(t.primeiraDoseDepoisDe(HOJE.atTime(10, 0))).contains(HOJE.plusDays(1).atTime(10, 2));
    }

    @Test
    void hojeForaDosDiasDoTratamentoNaoEhAfetado() {
        aceita(tratamento(HOJE, List.of(DayOfWeek.MONDAY), LocalTime.of(10, 2)), HOJE.atTime(10, 0));
    }

    @Test
    void tratamentoQueComecouAntesETemDoseHojeTambemSegueARegra() {
        Tratamento t = tratamento(HOJE.minusDays(30), QUINTA, LocalTime.of(10, 3));
        assertThatThrownBy(() -> t.validarAntecedencia(HOJE.atTime(10, 0), t.getHorarios()))
                .isInstanceOf(HorarioMuitoProximoException.class);
    }

    @Test
    void tratamentoQueTerminouOntemNaoTemDoseHoje() {
        Tratamento t = Tratamento.builder().medicamentoId(1L).dataInicio(HOJE.minusDays(30)).dataFim(HOJE.minusDays(1))
                .horarios(List.of(LocalTime.of(10, 2))).diasSemana(QUINTA).build();
        aceita(t, HOJE.atTime(10, 0));
        assertThat(t.primeiraDoseDepoisDe(HOJE.atTime(10, 0))).isEmpty();
    }

    @Test
    void naViradaDoDiaUmHorarioDaMadrugadaEhDoDiaSeguinteENaoConta() {
        // 23:58 com horário 00:02: o horário de "hoje" já passou; a primeira dose é amanhã às 00:02
        Tratamento t = tratamento(HOJE, List.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), LocalTime.of(0, 2));
        LocalDateTime agora = HOJE.atTime(23, 58);

        aceita(t, agora);
        assertThat(t.primeiraDoseDepoisDe(agora)).contains(HOJE.plusDays(1).atTime(0, 2));
    }

    @Test
    void pertoDaMeiaNoiteUmHorarioDeHojeAMenosDeCincoMinutosContinuaRecusado() {
        recusa(HOJE.atTime(23, 57), LocalTime.of(23, 59));
    }

    @Test
    void soOsHorariosVerificadosContam() {
        Tratamento t = tratamento(HOJE, QUINTA, LocalTime.of(10, 1), LocalTime.of(18, 0));
        // Edição: 10:01 já existia (não é verificado); 18:00 é novo e está longe
        assertThatCode(() -> t.validarAntecedencia(HOJE.atTime(10, 0), List.of(LocalTime.of(18, 0))))
                .doesNotThrowAnyException();
    }
}
