package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RemoverTokenDispositivoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private RemoverTokenDispositivoUseCase useCase;

    @Test
    void deveApagarSomenteOTokenDoCuidadorAutenticado() {
        useCase.executar(CUIDADOR_ID);

        verify(cuidadorRepository).removerFcmToken(CUIDADOR_ID);
        verifyNoMoreInteractions(cuidadorRepository);
    }

    @Test
    void deveSerIdempotente() {
        useCase.executar(CUIDADOR_ID);
        useCase.executar(CUIDADOR_ID);

        // Nenhuma exceção: chamar de novo (sem token gravado) só repete o UPDATE, que não altera nada
        verify(cuidadorRepository, times(2)).removerFcmToken(CUIDADOR_ID);
    }
}
