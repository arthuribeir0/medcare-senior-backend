package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.tratamento.DiasSemanaObrigatoriosException;
import com.medcare.senior.domain.tratamento.HorariosDuplicadosException;
import com.medcare.senior.domain.tratamento.HorariosObrigatoriosException;
import com.medcare.senior.domain.tratamento.PeriodoTratamentoInvalidoException;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EditarTratamentoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long TRATAMENTO_ID = 1000L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 14, 30);
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final List<LocalTime> NOVOS_HORARIOS = List.of(LocalTime.of(9, 0), LocalTime.of(21, 0));
    private static final List<DayOfWeek> NOVOS_DIAS = List.of(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY);

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @Mock
    private TratamentoRepositoryPort tratamentoRepository;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private GeradorDeDosesService geradorDeDoses;

    private EditarTratamentoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new EditarTratamentoUseCase(tratamentoRepository, doseRepository, geradorDeDoses, cacheDeRelatorio,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private void tratamentoDoCuidador() {
        Tratamento atual = Tratamento.builder()
                .id(TRATAMENTO_ID)
                .medicamentoId(100L)
                .dataInicio(INICIO)
                .dataFim(null)
                .horarios(List.of(LocalTime.of(8, 0)))
                .diasSemana(List.of(DayOfWeek.MONDAY))
                .build();
        when(tratamentoRepository.findByIdAndCuidadorId(TRATAMENTO_ID, CUIDADOR_ID)).thenReturn(Optional.of(atual));
    }

    private void verificarNadaAlterado() {
        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(doseRepository, geradorDeDoses);
    }

    @Test
    void deveSalvarNovaConfiguracaoERegenerarSomenteDosesPendentesFuturas() {
        tratamentoDoCuidador();
        when(tratamentoRepository.save(any(Tratamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Tratamento resultado = useCase.executar(TRATAMENTO_ID, INICIO.plusDays(60), NOVOS_HORARIOS, NOVOS_DIAS,
                CUIDADOR_ID);

        ArgumentCaptor<Tratamento> captor = ArgumentCaptor.forClass(Tratamento.class);
        InOrder ordem = inOrder(tratamentoRepository, doseRepository, geradorDeDoses);
        ordem.verify(tratamentoRepository).save(captor.capture());
        // Apaga só PENDENTES com horário depois de agora: registradas e passadas ficam intactas
        ordem.verify(doseRepository).deletePendentesFuturasByTratamentoId(TRATAMENTO_ID, AGORA);
        ordem.verify(geradorDeDoses).gerar(resultado);
        verify(cacheDeRelatorio).descartarDoCuidador(CUIDADOR_ID);

        Tratamento salvo = captor.getValue();
        assertThat(salvo.getId()).isEqualTo(TRATAMENTO_ID);
        assertThat(salvo.getMedicamentoId()).isEqualTo(100L);
        assertThat(salvo.getDataInicio()).isEqualTo(INICIO);
        assertThat(salvo.getDataFim()).isEqualTo(INICIO.plusDays(60));
        assertThat(salvo.getHorarios()).containsExactlyElementsOf(NOVOS_HORARIOS);
        assertThat(salvo.getDiasSemana()).containsExactlyElementsOf(NOVOS_DIAS);
    }

    @Test
    void deveAceitarTornarOTratamentoContinuo() {
        tratamentoDoCuidador();
        when(tratamentoRepository.save(any(Tratamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Tratamento resultado = useCase.executar(TRATAMENTO_ID, null, NOVOS_HORARIOS, NOVOS_DIAS, CUIDADOR_ID);

        assertThat(resultado.getDataFim()).isNull();
    }

    @Test
    void deveResponderNaoEncontradoParaTratamentoDeOutroCuidadorOuRemovido() {
        // O repositório devolve vazio tanto para outro cuidador quanto para tratamento/medicamento removido
        when(tratamentoRepository.findByIdAndCuidadorId(TRATAMENTO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(TRATAMENTO_ID, null, NOVOS_HORARIOS, NOVOS_DIAS, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Tratamento não encontrado");

        verificarNadaAlterado();
    }

    @Test
    void deveRejeitarHorariosDuplicados() {
        tratamentoDoCuidador();
        List<LocalTime> duplicados = List.of(LocalTime.of(9, 0), LocalTime.of(9, 0));

        assertThatThrownBy(() -> useCase.executar(TRATAMENTO_ID, null, duplicados, NOVOS_DIAS, CUIDADOR_ID))
                .isInstanceOf(HorariosDuplicadosException.class);

        verificarNadaAlterado();
    }

    @Test
    void deveRejeitarHorariosVazios() {
        tratamentoDoCuidador();

        assertThatThrownBy(() -> useCase.executar(TRATAMENTO_ID, null, List.of(), NOVOS_DIAS, CUIDADOR_ID))
                .isInstanceOf(HorariosObrigatoriosException.class);

        verificarNadaAlterado();
    }

    @Test
    void deveRejeitarDiasSemanaVazios() {
        tratamentoDoCuidador();

        assertThatThrownBy(() -> useCase.executar(TRATAMENTO_ID, null, NOVOS_HORARIOS, List.of(), CUIDADOR_ID))
                .isInstanceOf(DiasSemanaObrigatoriosException.class);

        verificarNadaAlterado();
    }

    @Test
    void deveRejeitarDataFimAnteriorADataInicioOriginal() {
        tratamentoDoCuidador();

        assertThatThrownBy(() -> useCase.executar(TRATAMENTO_ID, INICIO.minusDays(1), NOVOS_HORARIOS, NOVOS_DIAS,
                CUIDADOR_ID))
                .isInstanceOf(PeriodoTratamentoInvalidoException.class);

        verificarNadaAlterado();
    }
}
