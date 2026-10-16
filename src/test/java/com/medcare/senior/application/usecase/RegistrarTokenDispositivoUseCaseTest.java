package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.TokenDispositivoInvalidoException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrarTokenDispositivoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private RegistrarTokenDispositivoUseCase useCase;

    @Test
    void deveGravarOTokenSemEspacosERetirarDosOutrosCuidadores() {
        when(cuidadorRepository.atualizarFcmToken(CUIDADOR_ID, "token-fcm")).thenReturn(true);

        useCase.executar(CUIDADOR_ID, "  token-fcm  ");

        InOrder ordem = inOrder(cuidadorRepository);
        ordem.verify(cuidadorRepository).atualizarFcmToken(CUIDADOR_ID, "token-fcm");
        ordem.verify(cuidadorRepository).removerFcmTokenDosOutrosCuidadores("token-fcm", CUIDADOR_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void deveRejeitarTokenVazio(String token) {
        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, token))
                .isInstanceOf(TokenDispositivoInvalidoException.class);

        verifyNoInteractions(cuidadorRepository);
    }

    @Test
    void deveRejeitarTokenMaiorQueAColuna() {
        String grande = "a".repeat(RegistrarTokenDispositivoUseCase.TAMANHO_MAXIMO + 1);

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, grande))
                .isInstanceOf(TokenDispositivoInvalidoException.class);

        verifyNoInteractions(cuidadorRepository);
    }

    @Test
    void deveAceitarTokenNoTamanhoMaximo() {
        String limite = "a".repeat(RegistrarTokenDispositivoUseCase.TAMANHO_MAXIMO);
        when(cuidadorRepository.atualizarFcmToken(CUIDADOR_ID, limite)).thenReturn(true);

        useCase.executar(CUIDADOR_ID, limite);

        verify(cuidadorRepository).atualizarFcmToken(CUIDADOR_ID, limite);
    }

    @Test
    void contaInexistenteResponde404ENaoMexeEmOutrosCuidadores() {
        when(cuidadorRepository.atualizarFcmToken(CUIDADOR_ID, "token-fcm")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, "token-fcm"))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Cuidador não encontrado");

        verify(cuidadorRepository, never()).removerFcmTokenDosOutrosCuidadores(any(), anyLong());
    }
}
