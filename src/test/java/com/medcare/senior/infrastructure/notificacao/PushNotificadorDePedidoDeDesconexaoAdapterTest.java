package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.desconexao.NotificadorDePedidoDeDesconexaoPort;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificadorDePedidoDeDesconexaoAdapterTest {

    private static final PedidoDesconexaoNotificacao AVISO = new PedidoDesconexaoNotificacao(7L, 10L, 1L, "Maria");
    private static final String TOKEN = "token-do-aparelho";

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @Mock
    private NotificacaoPushPort notificacaoPush;

    private PushNotificadorDePedidoDeDesconexaoAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new PushNotificadorDePedidoDeDesconexaoAdapter(cuidadorRepository, notificacaoPush);
    }

    private void cuidadorComToken(String token) {
        when(cuidadorRepository.findById(1L)).thenReturn(Optional.of(Cuidador.builder().id(1L).fcmToken(token).build()));
    }

    @Test
    void enviaComTipoEIdsParaOAppAbrirOPedido() {
        cuidadorComToken(TOKEN);
        when(notificacaoPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(adapter.notificar(AVISO)).isEqualTo(ResultadoEnvioPush.ENVIADO);

        verify(notificacaoPush).enviar(TOKEN, "Pedido para sair do app",
                "Maria pediu para sair da conta no celular. Toque para autorizar ou recusar.",
                Map.of("tipo", "PEDIDO_DESCONEXAO", "pacienteId", "10", "pedidoId", "7"));
    }

    @Test
    void cuidadorSemTokenNaoEnvia() {
        cuidadorComToken(null);

        assertThat(adapter.notificar(AVISO)).isEqualTo(ResultadoEnvioPush.SEM_TOKEN);
        verify(notificacaoPush, never()).enviar(anyString(), anyString(), anyString(), any());
    }

    @Test
    void tokenInvalidoEDescartado() {
        cuidadorComToken(TOKEN);
        when(notificacaoPush.enviar(anyString(), anyString(), anyString(), any())).thenReturn(ResultadoEnvioPush.TOKEN_INVALIDO);

        assertThat(adapter.notificar(AVISO)).isEqualTo(ResultadoEnvioPush.TOKEN_INVALIDO);
        verify(cuidadorRepository).removerFcmTokenSeIgual(1L, TOKEN);
    }

    @Test
    void comFcmDesligadoOAvisoFicaSoNoLog() {
        new ApplicationContextRunner()
                .withBean(CuidadorRepositoryPort.class, () -> mock(CuidadorRepositoryPort.class))
                .withBean(NotificacaoPushPort.class, () -> mock(NotificacaoPushPort.class))
                .withUserConfiguration(LogNotificadorDePedidoDeDesconexaoAdapter.class,
                        PushNotificadorDePedidoDeDesconexaoAdapter.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(NotificadorDePedidoDeDesconexaoPort.class);
                    NotificadorDePedidoDeDesconexaoPort notificador = ctx.getBean(NotificadorDePedidoDeDesconexaoPort.class);
                    assertThat(notificador).isInstanceOf(LogNotificadorDePedidoDeDesconexaoAdapter.class);
                    assertThat(notificador.notificar(AVISO)).isEqualTo(ResultadoEnvioPush.PUSH_DESLIGADO);
                });
    }
}
