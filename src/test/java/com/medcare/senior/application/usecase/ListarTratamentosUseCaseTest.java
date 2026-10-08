package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarTratamentosUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;
    private static final Long MEDICAMENTO_ID = 100L;

    @Mock
    private TratamentoRepositoryPort tratamentoRepository;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @InjectMocks
    private ListarTratamentosUseCase useCase;

    @Test
    void deveListarTratamentosDoMedicamentoDoCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).idosoId(IDOSO_ID).build()));
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).cuidadorId(CUIDADOR_ID).build()));
        List<Tratamento> tratamentos = List.of(
                Tratamento.builder().id(1L).medicamentoId(MEDICAMENTO_ID).dataInicio(LocalDate.of(2026, 1, 1)).build());
        when(tratamentoRepository.findAllByMedicamentoId(MEDICAMENTO_ID)).thenReturn(tratamentos);

        assertThat(useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID)).isEqualTo(tratamentos);
    }

    @Test
    void deveRejeitarMedicamentoDeOutroCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).idosoId(IDOSO_ID).build()));
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");

        verifyNoInteractions(tratamentoRepository);
    }

    @Test
    void deveRejeitarMedicamentoInexistente() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verifyNoInteractions(tratamentoRepository);
    }
}
