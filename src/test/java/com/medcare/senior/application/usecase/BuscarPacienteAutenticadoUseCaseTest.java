package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarPacienteAutenticadoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private BuscarPacienteAutenticadoUseCase useCase;

    @Test
    void devolveOPacienteEONomeDeQuemCuida() {
        Paciente paciente = Paciente.builder().id(PACIENTE_ID).nome("José Souza").cuidadorId(CUIDADOR_ID).build();
        when(pacienteRepository.findById(PACIENTE_ID)).thenReturn(Optional.of(paciente));
        when(cuidadorRepository.findById(CUIDADOR_ID))
                .thenReturn(Optional.of(Cuidador.builder().id(CUIDADOR_ID).nome("Ana Souza").build()));

        BuscarPacienteAutenticadoUseCase.Resultado resultado = useCase.executar(PACIENTE_ID);

        assertThat(resultado.paciente()).isSameAs(paciente);
        assertThat(resultado.cuidadorNome()).isEqualTo("Ana Souza");
    }

    @Test
    void pacienteQueNaoExisteMaisTemSessaoInvalida() {
        when(pacienteRepository.findById(PACIENTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID)).isInstanceOf(SessaoInvalidaException.class);
        verifyNoInteractions(cuidadorRepository);
    }
}
