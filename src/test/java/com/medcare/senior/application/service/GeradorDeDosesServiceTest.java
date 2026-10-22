package com.medcare.senior.application.service;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.tratamento.Tratamento;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeradorDeDosesServiceTest {

    private static final Long TRATAMENTO_ID = 1000L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Quinta-feira, 14:30
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 14, 30);
    private static final LocalDate HOJE = AGORA.toLocalDate();
    private static final List<DayOfWeek> TODOS_OS_DIAS = List.copyOf(EnumSet.allOf(DayOfWeek.class));

    @Mock
    private DoseRepositoryPort doseRepository;

    private GeradorDeDosesService gerador;

    @BeforeEach
    void setUp() {
        gerador = new GeradorDeDosesService(doseRepository, Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO),
                GeradorDeDosesService.HORIZONTE_PADRAO_DIAS);
    }

    private static Tratamento tratamento(LocalDate inicio, LocalDate fim, List<DayOfWeek> dias, LocalTime... horarios) {
        return Tratamento.builder()
                .id(TRATAMENTO_ID)
                .medicamentoId(100L)
                .dataInicio(inicio)
                .dataFim(fim)
                .horarios(List.of(horarios))
                .diasSemana(dias)
                .build();
    }

    private void semDosesExistentes() {
        when(doseRepository.findHorariosProgramadosByTratamentoIdDesde(anyLong(), any())).thenReturn(Set.of());
    }

    @SuppressWarnings("unchecked")
    private List<Dose> dosesSalvas() {
        ArgumentCaptor<List<Dose>> captor = ArgumentCaptor.forClass(List.class);
        verify(doseRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static List<LocalDateTime> horarios(List<Dose> doses) {
        return doses.stream().map(Dose::getHorarioProgramado).toList();
    }

    @Test
    void deveGerarJanelaDeTrintaDiasParaTratamentoSemDataFim() {
        semDosesExistentes();

        int geradas = gerador.gerar(tratamento(HOJE.minusDays(10), null, TODOS_OS_DIAS,
                LocalTime.of(8, 0), LocalTime.of(20, 0)));

        List<Dose> doses = dosesSalvas();
        // Hoje: só 20:00 (08:00 já passou); depois, 30 dias x 2 horários
        assertThat(geradas).isEqualTo(61).isEqualTo(doses.size());
        assertThat(horarios(doses)).first().isEqualTo(HOJE.atTime(20, 0));
        assertThat(horarios(doses)).last().isEqualTo(HOJE.plusDays(30).atTime(20, 0));
        assertThat(doses).allSatisfy(dose -> {
            assertThat(dose.getStatus()).isEqualTo(StatusDose.PENDENTE);
            assertThat(dose.getTratamentoId()).isEqualTo(TRATAMENTO_ID);
            assertThat(dose.getHoraConfirmacao()).isNull();
        });
        // Idempotência: a consulta de horários existentes começa no primeiro dia da janela
        verify(doseRepository).findHorariosProgramadosByTratamentoIdDesde(TRATAMENTO_ID, HOJE.atStartOfDay());
    }

    @Test
    void devePararNaDataFimQuandoElaVemAntesDoFimDaJanela() {
        semDosesExistentes();

        gerador.gerar(tratamento(HOJE, HOJE.plusDays(3), TODOS_OS_DIAS, LocalTime.of(8, 0), LocalTime.of(20, 0)));

        List<LocalDateTime> horarios = horarios(dosesSalvas());
        assertThat(horarios).hasSize(1 + 3 * 2);
        assertThat(horarios).last().isEqualTo(HOJE.plusDays(3).atTime(20, 0));
    }

    @Test
    void deveComecarNaDataInicioQuandoElaEFutura() {
        semDosesExistentes();

        gerador.gerar(tratamento(HOJE.plusDays(5), null, TODOS_OS_DIAS, LocalTime.of(9, 0)));

        List<LocalDateTime> horarios = horarios(dosesSalvas());
        assertThat(horarios).first().isEqualTo(HOJE.plusDays(5).atTime(9, 0));
        assertThat(horarios).last().isEqualTo(HOJE.plusDays(30).atTime(9, 0));
        assertThat(horarios).hasSize(26);
    }

    @Test
    void deveGerarSomenteNosDiasDaSemanaDoTratamento() {
        semDosesExistentes();

        gerador.gerar(tratamento(HOJE, null, List.of(DayOfWeek.MONDAY), LocalTime.of(9, 0)));

        // Segundas entre 08/10/2026 e 07/11/2026: 12, 19 e 26/10 e 02/11
        assertThat(horarios(dosesSalvas())).containsExactly(
                LocalDateTime.of(2026, 10, 12, 9, 0),
                LocalDateTime.of(2026, 10, 19, 9, 0),
                LocalDateTime.of(2026, 10, 26, 9, 0),
                LocalDateTime.of(2026, 11, 2, 9, 0));
    }

    @Test
    void noDiaDeHojeDeveGerarSomenteHorariosPosterioresAoMomentoAtual() {
        semDosesExistentes();

        gerador.gerar(tratamento(HOJE, HOJE, TODOS_OS_DIAS,
                LocalTime.of(22, 0), LocalTime.of(8, 0), LocalTime.of(14, 30), LocalTime.of(14, 31)));

        // 14:30 é exatamente agora e não é gerado; a ordem de saída é cronológica
        assertThat(horarios(dosesSalvas())).containsExactly(HOJE.atTime(14, 31), HOJE.atTime(22, 0));
    }

    @Test
    void naoDeveDuplicarDosesJaExistentes() {
        when(doseRepository.findHorariosProgramadosByTratamentoIdDesde(TRATAMENTO_ID, HOJE.atStartOfDay()))
                .thenReturn(Set.of(HOJE.atTime(20, 0), HOJE.plusDays(1).atTime(20, 0)));

        int geradas = gerador.gerar(tratamento(HOJE, HOJE.plusDays(2), TODOS_OS_DIAS, LocalTime.of(20, 0)));

        assertThat(geradas).isEqualTo(1);
        assertThat(horarios(dosesSalvas())).containsExactly(HOJE.plusDays(2).atTime(20, 0));
    }

    @Test
    void naoDeveSalvarNadaQuandoTodasAsDosesJaExistem() {
        when(doseRepository.findHorariosProgramadosByTratamentoIdDesde(TRATAMENTO_ID, HOJE.atStartOfDay()))
                .thenReturn(Set.of(HOJE.atTime(20, 0), HOJE.plusDays(1).atTime(20, 0)));

        int geradas = gerador.gerar(tratamento(HOJE, HOJE.plusDays(1), TODOS_OS_DIAS, LocalTime.of(20, 0)));

        assertThat(geradas).isZero();
        verify(doseRepository, never()).saveAll(any());
    }

    @Test
    void tratamentoRemovidoNaoGeraDoses() {
        Tratamento removido = tratamento(HOJE, null, TODOS_OS_DIAS, LocalTime.of(20, 0));
        removido.setRemovidoEm(AGORA.minusHours(1));

        assertThat(gerador.gerar(removido)).isZero();

        verifyNoInteractions(doseRepository);
    }

    @Test
    void tratamentoJaEncerradoNaoGeraDoses() {
        assertThat(gerador.gerar(tratamento(HOJE.minusDays(20), HOJE.minusDays(1), TODOS_OS_DIAS,
                LocalTime.of(20, 0)))).isZero();

        verifyNoInteractions(doseRepository);
    }

    @Test
    void tratamentoQueComecaDepoisDaJanelaNaoGeraDosesAinda() {
        assertThat(gerador.gerar(tratamento(HOJE.plusDays(31), null, TODOS_OS_DIAS, LocalTime.of(20, 0)))).isZero();

        verifyNoInteractions(doseRepository);
    }

    // ---- horizonte configurável ---------------------------------------------------------------------------------

    @Test
    void oHorizontePadraoEhDeTrintaDias() {
        assertThat(gerador.horizonteDias()).isEqualTo(30);
    }

    @Test
    void horizonteConfiguradoLimitaAJanela() {
        GeradorDeDosesService seteDias = new GeradorDeDosesService(doseRepository,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO), 7);
        semDosesExistentes();

        int geradas = seteDias.gerar(tratamento(HOJE.minusDays(10), null, TODOS_OS_DIAS, LocalTime.of(20, 0)));

        // Hoje às 20:00 e mais 7 dias
        assertThat(geradas).isEqualTo(8);
        assertThat(horarios(dosesSalvas())).last().isEqualTo(HOJE.plusDays(7).atTime(20, 0));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 91, 365})
    void horizonteForaDe1A90DiasImpedeASubida(int dias) {
        Clock relogio = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        assertThatThrownBy(() -> new GeradorDeDosesService(doseRepository, relogio, dias))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("de 1 a 90 dias");
    }

    @Test
    void geracaoRepetidaNoMesmoDiaNaoDuplicaNada() {
        // Primeira chamada gera; na segunda, todos os horários já existem (o job e a edição podem rodar no mesmo dia)
        semDosesExistentes();
        Tratamento tratamento = tratamento(HOJE, null, TODOS_OS_DIAS, LocalTime.of(20, 0));
        int primeira = gerador.gerar(tratamento);
        Set<LocalDateTime> jaGeradas = Set.copyOf(horarios(dosesSalvas()));
        when(doseRepository.findHorariosProgramadosByTratamentoIdDesde(anyLong(), any())).thenReturn(jaGeradas);

        int segunda = gerador.gerar(tratamento);

        assertThat(primeira).isEqualTo(31);
        assertThat(segunda).isZero();
        verify(doseRepository, times(1)).saveAll(any());
    }
}
