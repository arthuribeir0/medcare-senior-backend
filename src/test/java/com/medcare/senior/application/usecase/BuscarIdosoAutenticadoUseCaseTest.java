package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuscarIdosoAutenticadoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @InjectMocks
    private BuscarIdosoAutenticadoUseCase useCase;

    @Test
    void devolveOIdosoEONomeDeQuemCuida() {
        Idoso idoso = Idoso.builder().id(IDOSO_ID).nome("José Souza").cuidadorId(CUIDADOR_ID).build();
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(idoso));
        when(cuidadorRepository.findById(CUIDADOR_ID))
                .thenReturn(Optional.of(Cuidador.builder().id(CUIDADOR_ID).nome("Ana Souza").build()));

        BuscarIdosoAutenticadoUseCase.Resultado resultado = useCase.executar(IDOSO_ID);

        assertThat(resultado.idoso()).isSameAs(idoso);
        assertThat(resultado.cuidadorNome()).isEqualTo("Ana Souza");
    }

    @Test
    void idosoQueNaoExisteMaisTemSessaoInvalida() {
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(IDOSO_ID)).isInstanceOf(SessaoInvalidaException.class);
        verifyNoInteractions(cuidadorRepository);
    }
}
