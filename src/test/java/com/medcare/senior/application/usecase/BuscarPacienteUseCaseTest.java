package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarPacienteUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @InjectMocks
    private BuscarPacienteUseCase useCase;

    @Test
    void deveDevolverPacienteDoCuidador() {
        Paciente paciente = Paciente.builder().id(PACIENTE_ID).nome("José").cuidadorId(CUIDADOR_ID).build();
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.of(paciente));

        assertThat(useCase.executar(PACIENTE_ID, CUIDADOR_ID)).isSameAs(paciente);
    }

    @Test
    void deveResponderNaoEncontradoParaPacienteDeOutroCuidadorOuInexistente() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Paciente não encontrado");
    }
}
