package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarPacientesUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @InjectMocks
    private ListarPacientesUseCase useCase;

    @Test
    void deveListarSomenteOsPacientesDoCuidadorNaOrdemDoRepositorio() {
        List<Paciente> pacientes = List.of(
                Paciente.builder().id(2L).nome("ana").cuidadorId(CUIDADOR_ID).build(),
                Paciente.builder().id(1L).nome("José").cuidadorId(CUIDADOR_ID).build());
        when(pacienteRepository.findAllByCuidadorId(CUIDADOR_ID)).thenReturn(pacientes);

        assertThat(useCase.executar(CUIDADOR_ID)).isEqualTo(pacientes);
    }

    @Test
    void deveRetornarListaVaziaQuandoCuidadorNaoTemPacientes() {
        when(pacienteRepository.findAllByCuidadorId(CUIDADOR_ID)).thenReturn(List.of());

        assertThat(useCase.executar(CUIDADOR_ID)).isEmpty();
    }
}
