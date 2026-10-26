package com.medcare.senior.application.service;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnvioDeAvisoServiceTest {

    private static final Long EVENTO_ID = 100L;
    private static final DoseNaoTomadaNotificacao NOTIFICACAO =
            new DoseNaoTomadaNotificacao(10L, 1L, 2L, "Maria", LocalDateTime.of(2026, 10, 16, 8, 0));

    @Mock
    private NotificadorDeDoseNaoTomadaPort notificador;

    @Mock
    private EventoNotificacaoRepositoryPort eventos;

    private EnvioDeAvisoService service;

    @BeforeEach
    void setUp() {
        service = new EnvioDeAvisoService(notificador, eventos);
    }

    @Test
    void enviadoMarcaOEventoComoEnviado() {
        when(notificador.notificar(NOTIFICACAO)).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.ENVIADO);
        verify(eventos).marcarEnviado(EVENTO_ID);
        verify(eventos, never()).marcarFalha(anyLong(), any());
    }

    @Test
    void tokenInvalidoEncerraOEventoComFalhaTokenInvalido() {
        when(notificador.notificar(NOTIFICACAO)).thenReturn(ResultadoEnvioPush.TOKEN_INVALIDO);

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.TOKEN_INVALIDO);
        verify(eventos).marcarFalha(EVENTO_ID, MotivoFalhaNotificacao.TOKEN_INVALIDO);
        verify(eventos, never()).marcarEnviado(anyLong());
    }

    @Test
    void semTokenEncerraOEventoComFalhaSemToken() {
        when(notificador.notificar(NOTIFICACAO)).thenReturn(ResultadoEnvioPush.SEM_TOKEN);

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.SEM_TOKEN);
        verify(eventos).marcarFalha(EVENTO_ID, MotivoFalhaNotificacao.SEM_TOKEN);
    }

    @Test
    void pushDesligadoEncerraOEventoComFalhaPushDesligadoSemRetentativa() {
        when(notificador.notificar(NOTIFICACAO)).thenReturn(ResultadoEnvioPush.PUSH_DESLIGADO);

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.PUSH_DESLIGADO);
        verify(eventos).marcarFalha(EVENTO_ID, MotivoFalhaNotificacao.PUSH_DESLIGADO);
        verify(eventos, never()).marcarEnviado(EVENTO_ID);
    }

    @Test
    void falhaTemporariaDeixaOEventoPendenteParaARetentativa() {
        when(notificador.notificar(NOTIFICACAO)).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
        verifyNoInteractions(eventos);
    }

    @Test
    void excecaoDoNotificadorEhTratadaComoFalhaTemporariaSemPropagar() {
        when(notificador.notificar(NOTIFICACAO)).thenThrow(new IllegalStateException("FCM fora do ar"));

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
        verifyNoInteractions(eventos);
    }

    @Test
    void resultadoNuloEhTratadoComoFalhaTemporaria() {
        when(notificador.notificar(NOTIFICACAO)).thenReturn(null);

        assertThat(service.enviar(EVENTO_ID, NOTIFICACAO)).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
        verifyNoInteractions(eventos);
    }
}
