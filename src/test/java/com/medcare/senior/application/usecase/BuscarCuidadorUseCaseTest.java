package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
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
class BuscarCuidadorUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private BuscarCuidadorUseCase useCase;

    @Test
    void deveDevolverOCuidadorAutenticado() {
        Cuidador cuidador = Cuidador.builder().id(CUIDADOR_ID).nome("Ana").email("ana@email.com").build();
        when(cuidadorRepository.findById(CUIDADOR_ID)).thenReturn(Optional.of(cuidador));

        assertThat(useCase.executar(CUIDADOR_ID)).isSameAs(cuidador);
    }

    @Test
    void deveResponderSessaoInvalidaParaContaExcluida() {
        when(cuidadorRepository.findById(CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID))
                .isInstanceOf(SessaoInvalidaException.class)
                .hasMessage("Sessão inválida. Entre na sua conta de novo.");
    }
}
