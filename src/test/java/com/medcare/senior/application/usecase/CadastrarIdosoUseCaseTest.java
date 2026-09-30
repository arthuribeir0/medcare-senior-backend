package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.IdosoJaCadastradoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
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
class CadastrarIdosoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final LocalDate NASCIMENTO = LocalDate.of(1940, 5, 10);

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private CadastrarIdosoUseCase useCase;

    @Test
    void deveCadastrarIdoso() {
        when(cuidadorRepository.existsById(CUIDADOR_ID)).thenReturn(true);
        when(idosoRepository.save(any(Idoso.class))).thenAnswer(invocacao -> {
            Idoso idoso = invocacao.getArgument(0);
            idoso.setId(10L);
            return idoso;
        });

        Idoso resultado = useCase.executar("  Maria Silva  ", NASCIMENTO, "11988887777", CUIDADOR_ID);

        assertThat(resultado.getId()).isEqualTo(10L);
        assertThat(resultado.getNome()).isEqualTo("Maria Silva");
        assertThat(resultado.getDataNascimento()).isEqualTo(NASCIMENTO);
        assertThat(resultado.getTelefone()).isEqualTo("11988887777");
        assertThat(resultado.getCuidadorId()).isEqualTo(CUIDADOR_ID);
    }

    @Test
    void deveRejeitarIdosoDuplicadoParaOMesmoCuidador() {
        when(idosoRepository.existsByCuidadorIdAndNomeAndDataNascimento(CUIDADOR_ID, "Maria Silva", NASCIMENTO))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar("Maria Silva", NASCIMENTO, null, CUIDADOR_ID))
                .isInstanceOf(IdosoJaCadastradoException.class);

        verify(idosoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarIdosoDuplicadoComVariacaoDeMaiusculasNoNome() {
        // A porta compara o nome sem diferenciar maiúsculas/minúsculas (contrato da RN-003); o mock simula isso
        when(idosoRepository.existsByCuidadorIdAndNomeAndDataNascimento(
                eq(CUIDADOR_ID), argThat(nome -> nome.equalsIgnoreCase("Maria Silva")), eq(NASCIMENTO)))
                .thenReturn(true);

        assertThatThrownBy(() -> useCase.executar("  MARIA silva ", NASCIMENTO, null, CUIDADOR_ID))
                .isInstanceOf(IdosoJaCadastradoException.class);

        verify(idosoRepository).existsByCuidadorIdAndNomeAndDataNascimento(CUIDADOR_ID, "MARIA silva", NASCIMENTO);
        verify(idosoRepository, never()).save(any());
    }

    @Test
    void devePermitirMesmoNomeEDataParaOutroCuidador() {
        Long outroCuidador = 2L;
        when(cuidadorRepository.existsById(outroCuidador)).thenReturn(true);
        when(idosoRepository.save(any(Idoso.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Idoso resultado = useCase.executar("Maria Silva", NASCIMENTO, null, outroCuidador);

        assertThat(resultado.getCuidadorId()).isEqualTo(outroCuidador);
        verify(idosoRepository).existsByCuidadorIdAndNomeAndDataNascimento(outroCuidador, "Maria Silva", NASCIMENTO);
    }

    @Test
    void deveRejeitarCuidadorInexistente() {
        when(cuidadorRepository.existsById(CUIDADOR_ID)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar("Maria Silva", NASCIMENTO, null, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);

        verify(idosoRepository, never()).save(any());
    }
}
