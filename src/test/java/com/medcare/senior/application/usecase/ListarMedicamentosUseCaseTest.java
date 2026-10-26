package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarMedicamentosUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @InjectMocks
    private ListarMedicamentosUseCase useCase;

    @Test
    void deveListarMedicamentosDoPacienteDoCuidador() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
        List<Medicamento> medicamentos = List.of(
                Medicamento.builder().id(1L).nome("Atenolol").pacienteId(PACIENTE_ID).build(),
                Medicamento.builder().id(2L).nome("Losartana").pacienteId(PACIENTE_ID).build());
        when(medicamentoRepository.findAllByPacienteId(PACIENTE_ID)).thenReturn(medicamentos);

        List<Medicamento> resultado = useCase.executar(PACIENTE_ID, CUIDADOR_ID);

        assertThat(resultado).isEqualTo(medicamentos);
    }

    @Test
    void deveRetornarListaVaziaQuandoPacienteNaoTemMedicamentos() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
        when(medicamentoRepository.findAllByPacienteId(PACIENTE_ID)).thenReturn(List.of());

        assertThat(useCase.executar(PACIENTE_ID, CUIDADOR_ID)).isEmpty();
    }

    @Test
    void deveRejeitarPacienteDeOutroCuidadorOuInexistente() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Paciente não encontrado");

        verifyNoInteractions(medicamentoRepository);
    }
}
