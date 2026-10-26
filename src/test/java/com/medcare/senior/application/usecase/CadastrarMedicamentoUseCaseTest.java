package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.DadosObrigatoriosMedicamentoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastrarMedicamentoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;

    @Mock
    private MedicamentoRepositoryPort medicamentoRepository;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @InjectMocks
    private CadastrarMedicamentoUseCase useCase;

    static Medicamento medicamentoValido() {
        return Medicamento.builder()
                .nome("Losartana")
                .dosagem("50mg")
                .tipo("COMPRIMIDO")
                .pacienteId(PACIENTE_ID)
                .build();
    }

    private void pacientePertenceAoCuidador() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
    }

    @Test
    void deveCadastrarMedicamento() {
        pacientePertenceAoCuidador();
        when(medicamentoRepository.save(any(Medicamento.class))).thenAnswer(invocacao -> invocacao.getArgument(0));
        Medicamento medicamento = medicamentoValido();
        medicamento.setId(999L); // id vindo do cliente deve ser ignorado

        Medicamento resultado = useCase.executar(medicamento, CUIDADOR_ID);

        assertThat(resultado.getId()).isNull();
        assertThat(resultado.getNome()).isEqualTo("Losartana");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRejeitarNomeAusente(String nome) {
        pacientePertenceAoCuidador();
        Medicamento medicamento = medicamentoValido();
        medicamento.setNome(nome);

        assertRejeitado(medicamento);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRejeitarDosagemAusente(String dosagem) {
        pacientePertenceAoCuidador();
        Medicamento medicamento = medicamentoValido();
        medicamento.setDosagem(dosagem);

        assertRejeitado(medicamento);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void deveRejeitarTipoAusente(String tipo) {
        pacientePertenceAoCuidador();
        Medicamento medicamento = medicamentoValido();
        medicamento.setTipo(tipo);

        assertRejeitado(medicamento);
    }

    @Test
    void deveRejeitarPacienteDeOutroCuidador() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(medicamentoValido(), CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(medicamentoRepository, never()).save(any());
    }

    private void assertRejeitado(Medicamento medicamento) {
        assertThatThrownBy(() -> useCase.executar(medicamento, CUIDADOR_ID))
                .isInstanceOf(DadosObrigatoriosMedicamentoException.class);

        verify(medicamentoRepository, never()).save(any());
    }
}
