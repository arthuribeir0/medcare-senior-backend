package com.medcare.senior.application.service;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import com.medcare.senior.domain.usuario.Role;
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
class AcessoAConversaServiceTest {

    private static final AuthenticatedUser CUIDADOR = new AuthenticatedUser(1L, Role.CUIDADOR);
    private static final AuthenticatedUser IDOSO = new AuthenticatedUser(10L, Role.IDOSO);

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @InjectMocks
    private AcessoAConversaService acesso;

    private static Idoso idoso(Long id) {
        return Idoso.builder().id(id).cuidadorId(1L).nome("Maria").build();
    }

    @Test
    void cuidadorAcessaAConversaDeUmIdosoSeu() {
        when(idosoRepository.findByIdAndCuidadorId(10L, 1L)).thenReturn(Optional.of(idoso(10L)));

        assertThat(acesso.resolver(CUIDADOR, 10L)).isEqualTo(new AcessoAConversaService.Conversa(10L, Remetente.CUIDADOR));
    }

    @Test
    void cuidadorPrecisaInformarOIdoso() {
        assertThatThrownBy(() -> acesso.resolver(CUIDADOR, null))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting("campo").isEqualTo("idosoId");
        verifyNoInteractions(idosoRepository);
    }

    @Test
    void idosoDeOutroCuidadorEhNaoEncontrado() {
        when(idosoRepository.findByIdAndCuidadorId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> acesso.resolver(CUIDADOR, 99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void idosoAcessaAPropriaConversaComOuSemInformarOId() {
        when(idosoRepository.findById(10L)).thenReturn(Optional.of(idoso(10L)));

        AcessoAConversaService.Conversa esperada = new AcessoAConversaService.Conversa(10L, Remetente.IDOSO);
        assertThat(acesso.resolver(IDOSO, null)).isEqualTo(esperada);
        assertThat(acesso.resolver(IDOSO, 10L)).isEqualTo(esperada);
    }

    @Test
    void idosoInformandoOutroIdosoEhAcessoNegado() {
        when(idosoRepository.findById(10L)).thenReturn(Optional.of(idoso(10L)));

        assertThatThrownBy(() -> acesso.resolver(IDOSO, 11L)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void idosoQueNaoExisteMaisTemSessaoInvalidaAntesDeQualquerOutraRecusa() {
        when(idosoRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> acesso.resolver(IDOSO, 11L)).isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> acesso.exigirIdoso(IDOSO)).isInstanceOf(SessaoInvalidaException.class);
    }
}
