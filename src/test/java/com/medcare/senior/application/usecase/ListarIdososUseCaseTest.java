package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarIdososUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @InjectMocks
    private ListarIdososUseCase useCase;

    @Test
    void deveListarSomenteOsIdososDoCuidadorNaOrdemDoRepositorio() {
        List<Idoso> idosos = List.of(
                Idoso.builder().id(2L).nome("ana").cuidadorId(CUIDADOR_ID).build(),
                Idoso.builder().id(1L).nome("José").cuidadorId(CUIDADOR_ID).build());
        when(idosoRepository.findAllByCuidadorId(CUIDADOR_ID)).thenReturn(idosos);

        assertThat(useCase.executar(CUIDADOR_ID)).isEqualTo(idosos);
    }

    @Test
    void deveRetornarListaVaziaQuandoCuidadorNaoTemIdosos() {
        when(idosoRepository.findAllByCuidadorId(CUIDADOR_ID)).thenReturn(List.of());

        assertThat(useCase.executar(CUIDADOR_ID)).isEmpty();
    }
}
