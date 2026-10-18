package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarMedicamentoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;
    private static final Long MEDICAMENTO_ID = 100L;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @InjectMocks
    private BuscarMedicamentoUseCase useCase;

    @Test
    void deveDevolverMedicamentoDeIdosoDoCuidador() {
        Medicamento medicamento = Medicamento.builder().id(MEDICAMENTO_ID).nome("Losartana").idosoId(IDOSO_ID).build();
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.of(medicamento));
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).cuidadorId(CUIDADOR_ID).build()));

        assertThat(useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID)).isSameAs(medicamento);
    }

    @Test
    void deveResponderNaoEncontradoParaMedicamentoDeOutroCuidador() {
        when(medicamentoRepository.findById(MEDICAMENTO_ID))
                .thenReturn(Optional.of(Medicamento.builder().id(MEDICAMENTO_ID).idosoId(IDOSO_ID).build()));
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");
    }

    @Test
    void deveResponderNaoEncontradoParaMedicamentoInexistenteOuRemovido() {
        // O repositório ignora removidos: para o use case, removido = inexistente
        when(medicamentoRepository.findById(MEDICAMENTO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(MEDICAMENTO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Medicamento não encontrado");
        verifyNoInteractions(idosoRepository);
    }
}
