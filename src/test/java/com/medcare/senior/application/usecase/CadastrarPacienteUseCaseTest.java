package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.PacienteJaCadastradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastrarPacienteUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final LocalDate NASCIMENTO = LocalDate.of(1940, 5, 10);

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private CadastrarPacienteUseCase useCase;

    @Test
    void deveCadastrarPaciente() {
        when(cuidadorRepository.existsById(CUIDADOR_ID)).thenReturn(true);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocacao -> {
            Paciente paciente = invocacao.getArgument(0);
            paciente.setId(10L);
            return paciente;
        });

        Paciente resultado = useCase.executar("  Maria Silva  ", NASCIMENTO, "11988887777", CUIDADOR_ID);

        assertThat(resultado.getId()).isEqualTo(10L);
        assertThat(resultado.getNome()).isEqualTo("Maria Silva");
        assertThat(resultado.getDataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(resultado.getTelefone()).isEqualTo("11988887777");
        assertThat(resultado.getCuidadorId()).isEqualTo(CUIDADOR_ID);
    }

    @Test
    void deveRejeitarPacienteDuplicadoParaOMesmoCuidador() {
        when(pacienteRepository.existsByCuidadorIdAndNomeAndDataNascimento(CUIDADOR_ID, "Maria Silva", NASCIMENTO))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar("Maria Silva", NASCIMENTO, null, CUIDADOR_ID))
                .isInstanceOf(PacienteJaCadastradoException.class);

        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void deveRejeitarPacienteDuplicadoComVariacaoDeMaiusculasNoNome() {
        // A porta compara o nome sem diferenciar maiúsculas/minúsculas (contrato da RN-003); o mock simula isso
        when(pacienteRepository.existsByCuidadorIdAndNomeAndDataNascimento(
                eq(CUIDADOR_ID), argThat(nome -> nome.equalsIgnoreCase("Maria Silva")), eq(NASCIMENTO)))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar("  MARIA silva ", NASCIMENTO, null, CUIDADOR_ID))
                .isInstanceOf(PacienteJaCadastradoException.class);

        verify(pacienteRepository).existsByCuidadorIdAndNomeAndDataNascimento(CUIDADOR_ID, "MARIA silva", NASCIMENTO);
        verify(pacienteRepository, never()).save(any());
    }

    @Test
    void devePermitirMesmoNomeEDataParaOutroCuidador() {
        Long outroCuidador = 2L;
        when(cuidadorRepository.existsById(outroCuidador)).thenReturn(true);
        when(pacienteRepository.save(any(Paciente.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Paciente resultado = useCase.executar("Maria Silva", NASCIMENTO, null, outroCuidador);

        assertThat(resultado.getCuidadorId()).isEqualTo(outroCuidador);
        verify(pacienteRepository).existsByCuidadorIdAndNomeAndDataNascimento(outroCuidador, "Maria Silva", NASCIMENTO);
    }

    @Test
    void deveRejeitarCuidadorInexistente() {
        when(cuidadorRepository.existsById(CUIDADOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar("Maria Silva", NASCIMENTO, null, CUIDADOR_ID))
                .isInstanceOf(SessaoInvalidaException.class);

        verify(pacienteRepository, never()).save(any());
    }
}
