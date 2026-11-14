/*
 * Testes automatizados de salvar tratamento (11 casos). Cobre: deve salvar tratamento valido; deve aceitar
 * tratamento continuo sem data fim; deve aceitar data fim igual a data início; deve rejeitar horários duplicados;
 * deve rejeitar horários vazios; deve rejeitar dias semana vazios; deve rejeitar data fim anterior a data início;
 * deve rejeitar medicamento de outro cuidador; entre outros.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.DiasSemanaObrigatoriosException;
import com.medcare.senior.domain.tratamento.HorariosDuplicadosException;
import com.medcare.senior.domain.tratamento.HorariosObrigatoriosException;
import com.medcare.senior.domain.tratamento.PeriodoTratamentoInvalidoException;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.HorarioMuitoProximoException;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalvarTratamentoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;
    private static final Long MEDICAMENTO_ID = 100L;
    private static final LocalDate INICIO = LocalDate.of(2026, 10, 1);
    private static final List<LocalTime> HORARIOS = List.of(LocalTime.of(8, 0), LocalTime.of(20, 0));
    private static final List<DayOfWeek> DIAS = List.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY);
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @Mock
    private TratamentoRepositoryPort tratamentoRepository;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private GeradorDeDosesService geradorDeDoses;

    private SalvarTratamentoUseCase useCase;

    // Antes do início dos tratamentos dos testes: a antecedência mínima não se aplica (testada em AntecedenciaDoHorarioTest)
    @BeforeEach
    void setUp() {
        useCase = new SalvarTratamentoUseCase(tratamentoRepository, medicamentoRepository, pacienteRepository, geradorDeDoses,
                cacheDeRelatorio, Clock.fixed(LocalDateTime.of(2026, 9, 30, 12, 0).atZone(FUSO).toInstant(), FUSO));
    }

    private void medicamentoDoCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).pacienteId(PACIENTE_ID).build()));
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
    }

    @Test
    void deveSalvarTratamentoValido() {
        medicamentoDoCuidador();
        when(tratamentoRepository.save(any(Tratamento.class))).thenAnswer(invocacao -> {
            Tratamento salvo = invocacao.getArgument(0);
            salvo.setId(1000L);
            return salvo;
        });

        Tratamento resultado = useCase.executar(MEDICAMENTO_ID, INICIO, INICIO.plusDays(30), HORARIOS, DIAS, CUIDADOR_ID).tratamento();

        assertThat(resultado.getId()).isEqualTo(1000L);
        ArgumentCaptor<Tratamento> captor = ArgumentCaptor.forClass(Tratamento.class);
        verify(tratamentoRepository).save(captor.capture());
        Tratamento enviado = captor.getValue();
        assertThat(enviado.getMedicamentoId()).isEqualTo(MEDICAMENTO_ID);
        assertThat(enviado.getDataInicio()).isEqualTo(INICIO);
        assertThat(enviado.getDataFim()).isEqualTo(INICIO.plusDays(30));
        assertThat(enviado.getHorarios()).containsExactlyElementsOf(HORARIOS);
        assertThat(enviado.getDiasSemana()).containsExactlyElementsOf(DIAS);

        // As doses da janela inicial são geradas para o tratamento já salvo (com id)
        verify(geradorDeDoses).gerar(resultado);
        verify(cacheDeRelatorio).descartarDoCuidador(CUIDADOR_ID);
    }

    @Test
    void deveAceitarTratamentoContinuoSemDataFim() {
        medicamentoDoCuidador();
        when(tratamentoRepository.save(any(Tratamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Tratamento resultado = useCase.executar(MEDICAMENTO_ID, INICIO, null, HORARIOS, DIAS, CUIDADOR_ID).tratamento();

        assertThat(resultado.getDataFim()).isNull();
    }

    @Test
    void deveAceitarDataFimIgualADataInicio() {
        medicamentoDoCuidador();
        when(tratamentoRepository.save(any(Tratamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Tratamento resultado = useCase.executar(MEDICAMENTO_ID, INICIO, INICIO, HORARIOS, DIAS, CUIDADOR_ID).tratamento();

        assertThat(resultado.getDataFim()).isEqualTo(INICIO);
    }

    @Test
    void deveRejeitarHorariosDuplicados() {
        medicamentoDoCuidador();
        List<LocalTime> duplicados = List.of(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(8, 0));

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, INICIO, null, duplicados, DIAS, CUIDADOR_ID))
                .isInstanceOf(HorariosDuplicadosException.class)
                .hasMessageContaining("08:00");

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void deveRejeitarHorariosVazios(List<LocalTime> horarios) {
        medicamentoDoCuidador();

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, INICIO, null, horarios, DIAS, CUIDADOR_ID))
                .isInstanceOf(HorariosObrigatoriosException.class);

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void deveRejeitarDiasSemanaVazios(List<DayOfWeek> dias) {
        medicamentoDoCuidador();

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, INICIO, null, HORARIOS, dias, CUIDADOR_ID))
                .isInstanceOf(DiasSemanaObrigatoriosException.class);

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    @Test
    void deveRejeitarDataFimAnteriorADataInicio() {
        medicamentoDoCuidador();

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, INICIO, INICIO.minusDays(1), HORARIOS, DIAS, CUIDADOR_ID))
                .isInstanceOf(PeriodoTratamentoInvalidoException.class);

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    @Test
    void deveRejeitarMedicamentoDeOutroCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).pacienteId(PACIENTE_ID).build()));
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, INICIO, null, HORARIOS, DIAS, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    @Test
    void deveRejeitarMedicamentoInexistente() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, INICIO, null, HORARIOS, DIAS, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    // ---- antecedência mínima (relógio às 10:00 de quinta-feira, 08/10/2026) ---------------------------------------

    private SalvarTratamentoUseCase useCaseAs10h() {
        LocalDateTime agora = LocalDateTime.of(2026, 10, 8, 10, 0);
        return new SalvarTratamentoUseCase(tratamentoRepository, medicamentoRepository, pacienteRepository, geradorDeDoses,
                cacheDeRelatorio, Clock.fixed(agora.atZone(FUSO).toInstant(), FUSO));
    }

    @Test
    void horarioDeHojeAMenosDeCincoMinutosEhRecusadoSemSalvarNada() {
        medicamentoDoCuidador();
        LocalDate hoje = LocalDate.of(2026, 10, 8);

        assertThatThrownBy(() -> useCaseAs10h().executar(MEDICAMENTO_ID, hoje, null, List.of(LocalTime.of(10, 3)),
                List.of(DayOfWeek.THURSDAY), CUIDADOR_ID))
                .isInstanceOf(HorarioMuitoProximoException.class);

        verify(tratamentoRepository, never()).save(any());
        verifyNoInteractions(geradorDeDoses);
    }

    @Test
    void horarioQueJaPassouHojeEhAceitoEAPrimeiraDoseVemNaResposta() {
        medicamentoDoCuidador();
        when(tratamentoRepository.save(any(Tratamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        LocalDate hoje = LocalDate.of(2026, 10, 8);

        TratamentoSalvo salvo = useCaseAs10h().executar(MEDICAMENTO_ID, hoje, null, List.of(LocalTime.of(8, 0)),
                List.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), CUIDADOR_ID);

        assertThat(salvo.primeiraDose()).isEqualTo(LocalDateTime.of(2026, 10, 9, 8, 0));
    }
}
