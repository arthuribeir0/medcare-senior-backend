package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoComTratamentoAtivoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoverMedicamentoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;
    private static final Long MEDICAMENTO_ID = 100L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 14, 30);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @Mock
    private TratamentoRepositoryPort tratamentoRepository;

    @Mock
    private DoseRepositoryPort doseRepository;

    private RemoverMedicamentoUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        useCase = new RemoverMedicamentoUseCase(medicamentoRepository, idosoRepository, tratamentoRepository,
                doseRepository, cacheDeRelatorio, clock);
    }

    private void medicamentoDoCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).idosoId(IDOSO_ID).build()));
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).cuidadorId(CUIDADOR_ID).build()));
    }

    private void tratamentos(Tratamento... tratamentos) {
        when(tratamentoRepository.findAllByMedicamentoId(MEDICAMENTO_ID)).thenReturn(List.of(tratamentos));
    }

    private static Tratamento terminandoEm(LocalDate dataFim) {
        return Tratamento.builder().medicamentoId(MEDICAMENTO_ID).dataInicio(HOJE.minusDays(60)).dataFim(dataFim).build();
    }

    private void verificarRemovidoLogicamente() {
        InOrder ordem = inOrder(doseRepository, tratamentoRepository, medicamentoRepository);
        // Só as doses PENDENTES futuras; passadas e registradas ficam (RN-008)
        ordem.verify(doseRepository).deletePendentesFuturasByMedicamentoId(MEDICAMENTO_ID, AGORA);
        ordem.verify(tratamentoRepository).marcarComoRemovidosPorMedicamento(MEDICAMENTO_ID, AGORA);
        ordem.verify(medicamentoRepository).marcarComoRemovido(MEDICAMENTO_ID, AGORA);
        verify(cacheDeRelatorio).descartarDoCuidador(CUIDADOR_ID);
    }

    private void verificarNadaRemovido() {
        verifyNoInteractions(doseRepository);
        verify(tratamentoRepository, never()).marcarComoRemovidosPorMedicamento(anyLong(), any());
        verify(medicamentoRepository, never()).marcarComoRemovido(anyLong(), any());
    }

    @Test
    void deveMarcarComoRemovidoMedicamentoSemTratamento() {
        medicamentoDoCuidador();
        tratamentos();

        useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, false);

        verificarRemovidoLogicamente();
    }

    @Test
    void deveRemoverSemConfirmacaoQuandoTodosOsTratamentosJaTerminaram() {
        medicamentoDoCuidador();
        tratamentos(terminandoEm(HOJE.minusDays(1)));

        useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, false);

        verificarRemovidoLogicamente();
    }

    @Test
    void deveBloquearRemocaoComTratamentoContinuoSemConfirmacao() {
        medicamentoDoCuidador();
        tratamentos(terminandoEm(null));

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, false))
                .isInstanceOf(MedicamentoComTratamentoAtivoException.class);

        verificarNadaRemovido();
    }

    @Test
    void deveBloquearRemocaoComTratamentoTerminandoHojeSemConfirmacao() {
        medicamentoDoCuidador();
        tratamentos(terminandoEm(HOJE.minusDays(10)), terminandoEm(HOJE));

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, false))
                .isInstanceOf(MedicamentoComTratamentoAtivoException.class);

        verificarNadaRemovido();
    }

    @Test
    void deveRemoverComTratamentoAtivoQuandoForcado() {
        medicamentoDoCuidador();
        tratamentos(terminandoEm(HOJE.plusDays(5)));

        useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, true);

        verificarRemovidoLogicamente();
    }

    @Test
    void deveResponderNaoEncontradoParaMedicamentoJaRemovido() {
        // O repositório ignora medicamentos removidos: para o use case, removido = inexistente
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, true))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");

        verificarNadaRemovido();
    }

    @Test
    void deveRejeitarMedicamentoDeOutroCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).idosoId(IDOSO_ID).build()));
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID, true))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");

        verificarNadaRemovido();
    }
}
