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
    private static final AuthenticatedUser IDOSO = new AuthenticatedUser(10L, Role.IDOSO);

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
            return new Mensagem(301L, m.idosoId(), m.remetente(), m.conteudo(), m.criadaEm(), m.lidaEm());
        });

        Mensagem enviada = new EnviarMensagemUseCase(acesso, repositorio, clock).executar(CUIDADOR, 10L, " Bom dia ");

        assertThat(enviada.id()).isEqualTo(301L);
        assertThat(enviada.idosoId()).isEqualTo(10L);
        assertThat(enviada.remetente()).isEqualTo(Remetente.CUIDADOR);
        assertThat(enviada.conteudo()).isEqualTo("Bom dia");
        assertThat(enviada.criadaEm()).isEqualTo(AGORA);
        assertThat(enviada.lidaEm()).isNull();
    }

    @Test
    void enviarVazioNaoGrava() {
        when(acesso.resolver(IDOSO, null)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.IDOSO));

        assertThatThrownBy(() -> new EnviarMensagemUseCase(acesso, repositorio, clock).executar(IDOSO, null, "   "))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void listarValidaAPaginaAntesDeConsultarEUsaOPadrao() {
        ListarMensagensUseCase listar = new ListarMensagensUseCase(acesso, repositorio);
        assertThatThrownBy(() -> listar.executar(IDOSO, null, 0, 101)).isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(acesso, repositorio);

        when(acesso.resolver(IDOSO, null)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.IDOSO));
        Pagina<Mensagem> pagina = new Pagina<>(List.of(), 0, 20, 0);
        when(repositorio.findConversa(10L, 0, 20)).thenReturn(pagina);

        assertThat(listar.executar(IDOSO, null, null, null)).isSameAs(pagina);
    }

    @Test
    void marcarComoLidasMarcaSoAsDoOutroLadoComAHoraDoServidor() {
        when(acesso.resolver(CUIDADOR, 10L)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.CUIDADOR));
        when(acesso.resolver(IDOSO, null)).thenReturn(new AcessoAConversaService.Conversa(10L, Remetente.IDOSO));
        when(repositorio.marcarComoLidas(10L, Remetente.IDOSO, AGORA)).thenReturn(3);
        when(repositorio.marcarComoLidas(10L, Remetente.CUIDADOR, AGORA)).thenReturn(0);
        MarcarMensagensComoLidasUseCase marcar = new MarcarMensagensComoLidasUseCase(acesso, repositorio, clock);

        // O cuidador lê as do idoso; o idoso, as do cuidador
        assertThat(marcar.executar(CUIDADOR, 10L)).isEqualTo(3);
        assertThat(marcar.executar(IDOSO, null)).isZero();
    }

    @Test
    void conversasDoCuidadorEDoIdoso() {
        ResumoConversa doIdoso = new ResumoConversa(10L, "Maria", null, 0);
        when(repositorio.findConversasDoCuidador(1L)).thenReturn(List.of(doIdoso));
        when(acesso.exigirIdoso(IDOSO)).thenReturn(10L);
        when(repositorio.findConversaDoIdoso(10L)).thenReturn(doIdoso);
        ListarConversasUseCase listar = new ListarConversasUseCase(acesso, repositorio);

        assertThat(listar.executar(CUIDADOR)).containsExactly(doIdoso);
        assertThat(listar.executar(IDOSO)).containsExactly(doIdoso);
        verify(acesso).exigirIdoso(IDOSO);
    }

    @Test
    void naoLidasDoCuidadorSomamTodasAsConversasEAsDoIdosoSaoAsDoCuidador() {
        when(repositorio.contarNaoLidasDoCuidador(1L)).thenReturn(4L);
        when(acesso.exigirIdoso(IDOSO)).thenReturn(10L);
        when(repositorio.contarNaoLidas(10L, Remetente.CUIDADOR)).thenReturn(2L);
        ContarMensagensNaoLidasUseCase contar = new ContarMensagensNaoLidasUseCase(acesso, repositorio);

        assertThat(contar.executar(CUIDADOR)).isEqualTo(4L);
        assertThat(contar.executar(IDOSO)).isEqualTo(2L);
        ArgumentCaptor<Remetente> lado = ArgumentCaptor.forClass(Remetente.class);
        verify(repositorio).contarNaoLidas(any(), lado.capture());
        assertThat(lado.getValue()).isEqualTo(Remetente.CUIDADOR);
    }
}
