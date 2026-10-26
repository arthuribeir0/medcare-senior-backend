package com.medcare.senior.application.service;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
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
    private static final AuthenticatedUser PACIENTE = new AuthenticatedUser(10L, Role.PACIENTE);

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @InjectMocks
    private AcessoAConversaService acesso;

    private static Paciente paciente(Long id) {
        return Paciente.builder().id(id).cuidadorId(1L).nome("Maria").build();
    }

    @Test
    void cuidadorAcessaAConversaDeUmPacienteSeu() {
        when(pacienteRepository.findByIdAndCuidadorId(10L, 1L)).thenReturn(Optional.of(paciente(10L)));

        assertThat(acesso.resolver(CUIDADOR, 10L)).isEqualTo(new AcessoAConversaService.Conversa(10L, Remetente.CUIDADOR));
    }

    @Test
    void cuidadorPrecisaInformarOPaciente() {
        assertThatThrownBy(() -> acesso.resolver(CUIDADOR, null))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting("campo").isEqualTo("pacienteId");
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void pacienteDeOutroCuidadorEhNaoEncontrado() {
        when(pacienteRepository.findByIdAndCuidadorId(99L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> acesso.resolver(CUIDADOR, 99L)).isInstanceOf(RecursoNaoEncontradoException.class);
    }

    @Test
    void pacienteAcessaAPropriaConversaComOuSemInformarOId() {
        when(pacienteRepository.findById(10L)).thenReturn(Optional.of(paciente(10L)));

        AcessoAConversaService.Conversa esperada = new AcessoAConversaService.Conversa(10L, Remetente.PACIENTE);
        assertThat(acesso.resolver(PACIENTE, null)).isEqualTo(esperada);
        assertThat(acesso.resolver(PACIENTE, 10L)).isEqualTo(esperada);
    }

    @Test
    void pacienteInformandoOutroPacienteEhAcessoNegado() {
        when(pacienteRepository.findById(10L)).thenReturn(Optional.of(paciente(10L)));

        assertThatThrownBy(() -> acesso.resolver(PACIENTE, 11L)).isInstanceOf(AcessoNegadoException.class);
    }

    @Test
    void pacienteQueNaoExisteMaisTemSessaoInvalidaAntesDeQualquerOutraRecusa() {
        when(pacienteRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> acesso.resolver(PACIENTE, 11L)).isInstanceOf(SessaoInvalidaException.class);
        assertThatThrownBy(() -> acesso.exigirPaciente(PACIENTE)).isInstanceOf(SessaoInvalidaException.class);
    }
}
