package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.AcessoAConversaService;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.mensagem.Mensagem;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.mensagem.ResumoConversa;
import com.medcare.senior.domain.usuario.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Casos de uso das mensagens (UC-010) com o acesso à conversa e o repositório simulados; as regras de acesso de cada
 * papel estão em AcessoAConversaServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class MensagemUseCasesTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 20, 9, 30);
    private static final AuthenticatedUser CUIDADOR = new AuthenticatedUser(1L, Role.CUIDADOR);
    private static final AuthenticatedUser PACIENTE = new AuthenticatedUser(10L, Role.PACIENTE);

    @Mock
    private AcessoAConversaService acesso;

    @Mock
    private MensagemRepositoryPort repositorio;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
    }

    @Test
    void enviarGravaNoLadoDeQuemEscreveComAHoraDoServidor() {
        when(acesso.resolver(CUIDADOR, 10L)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.CUIDADOR));
        when(repositorio.salvar(any())).thenAnswer(i -> {
            Mensagem m = i.getArgument(0);
            return new Mensagem(301L, m.pacienteId(), m.remetente(), m.conteudo(), m.criadaEm(), m.lidaEm());
        });

        Mensagem enviada = new EnviarMensagemUseCase(acesso, repositorio, clock).executar(CUIDADOR, 10L, " Bom dia ");

        assertThat(enviada.id()).isEqualTo(301L);
        assertThat(enviada.pacienteId()).isEqualTo(10L);
        assertThat(enviada.remetente()).isEqualTo(Remetente.CUIDADOR);
        assertThat(enviada.conteudo()).isEqualTo("Bom dia");
        assertThat(enviada.criadaEm()).isEqualTo(AGORA);
        assertThat(enviada.lidaEm()).isNull();
    }

    @Test
    void enviarVazioNaoGrava() {
        when(acesso.resolver(PACIENTE, null)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.PACIENTE));

        assertThatThrownBy(() -> new EnviarMensagemUseCase(acesso, repositorio, clock).executar(PACIENTE, null, "   "))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void listarValidaAPaginaAntesDeConsultarEUsaOPadrao() {
        ListarMensagensUseCase listar = new ListarMensagensUseCase(acesso, repositorio);
        assertThatThrownBy(() -> listar.executar(PACIENTE, null, 0, 101)).isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(acesso, repositorio);

        when(acesso.resolver(PACIENTE, null)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.PACIENTE));
        Pagina<Mensagem> pagina = new Pagina<>(List.of(), 0, 20, 0);
        when(repositorio.findConversa(10L, 0, 20)).thenReturn(pagina);

        assertThat(listar.executar(PACIENTE, null, null, null)).isSameAs(pagina);
    }

    @Test
    void marcarComoLidasMarcaSoAsDoOutroLadoComAHoraDoServidor() {
        when(acesso.resolver(CUIDADOR, 10L)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.CUIDADOR));
        when(acesso.resolver(PACIENTE, null)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.PACIENTE));
        when(repositorio.marcarComoLidas(10L, Remetente.PACIENTE, AGORA)).thenReturn(3);
        when(repositorio.marcarComoLidas(10L, Remetente.CUIDADOR, AGORA)).thenReturn(0);
        MarcarMensagensComoLidasUseCase marcar = new MarcarMensagensComoLidasUseCase(acesso, repositorio, clock);

        // O cuidador lê as do paciente; o paciente, as do cuidador
        assertThat(marcar.executar(CUIDADOR, 10L)).isEqualTo(3);
        assertThat(marcar.executar(PACIENTE, null)).isZero();
    }

    @Test
    void conversasDoCuidadorEDoPaciente() {
        ResumoConversa doPaciente = new ResumoConversa(10L, "Maria", null, 0);
        when(repositorio.findConversasDoCuidador(1L)).thenReturn(List.of(doPaciente));
        when(acesso.exigirPaciente(PACIENTE)).thenReturn(10L);
        when(repositorio.findConversaDoPaciente(10L)).thenReturn(doPaciente);
        ListarConversasUseCase listar = new ListarConversasUseCase(acesso, repositorio);

        assertThat(listar.executar(CUIDADOR)).containsExactly(doPaciente);
        assertThat(listar.executar(PACIENTE)).containsExactly(doPaciente);
        verify(acesso).exigirPaciente(PACIENTE);
    }

    @Test
    void naoLidasDoCuidadorSomamTodasAsConversasEAsDoPacienteSaoAsDoCuidador() {
        when(repositorio.contarNaoLidasDoCuidador(1L)).thenReturn(4L);
        when(acesso.exigirPaciente(PACIENTE)).thenReturn(10L);
        when(repositorio.contarNaoLidas(10L, Remetente.CUIDADOR)).thenReturn(2L);
        ContarMensagensNaoLidasUseCase contar = new ContarMensagensNaoLidasUseCase(acesso, repositorio);

        assertThat(contar.executar(CUIDADOR)).isEqualTo(4L);
        assertThat(contar.executar(PACIENTE)).isEqualTo(2L);
        ArgumentCaptor<Remetente> lado = ArgumentCaptor.forClass(Remetente.class);
        verify(repositorio).contarNaoLidas(any(), lado.capture());
        assertThat(lado.getValue()).isEqualTo(Remetente.CUIDADOR);
    }
}
