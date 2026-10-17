package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
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
class BuscarIdosoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @InjectMocks
    private BuscarIdosoUseCase useCase;

    @Test
    void deveDevolverIdosoDoCuidador() {
        Idoso idoso = Idoso.builder().id(IDOSO_ID).nome("José").cuidadorId(CUIDADOR_ID).build();
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.of(idoso));

        assertThat(useCase.executar(IDOSO_ID, CUIDADOR_ID)).isSameAs(idoso);
    }

    @Test
    void deveResponderNaoEncontradoParaIdosoDeOutroCuidadorOuInexistente() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(IDOSO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Idoso não encontrado");
    }
}
