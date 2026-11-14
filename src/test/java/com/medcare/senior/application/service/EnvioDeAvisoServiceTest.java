/*
 * Testes automatizados de envio de aviso (10 casos). Cobre: enviado marca o evento como enviado; token inválido
 * encerra o evento com falha token inválido; sem token encerra o evento com falha sem token; push desligado
 * encerra o evento com falha push desligado sem retentativa; falha temporária deixa o evento pendente para a
 * retentativa; exceção do notificador é tratada como falha temporária sem propagar; resultado nulo é tratado como
 * falha temporária; lembrete enviado marca o evento como enviado sem tocar no notificador do cuidador; entre
 * outros.
 */
package com.medcare.senior.application.service;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.NotificadorDeLembreteDoPacientePort;
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
    private NotificadorDeLembreteDoPacientePort notificadorDoPaciente;

    @Mock
    private EventoNotificacaoRepositoryPort eventos;

    private EnvioDeAvisoService service;

    private static final LembreteDoPacienteNotificacao LEMBRETE = new LembreteDoPacienteNotificacao(10L, 2L, "Losartana",
            LocalDateTime.of(2026, 10, 16, 8, 0), LocalDateTime.of(2026, 10, 16, 8, 30));

    @BeforeEach
    void setUp() {
        service = new EnvioDeAvisoService(notificador, notificadorDoPaciente, eventos);
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

    // ---- lembrete ao paciente: mesmo registro do resultado, pelo notificador do paciente ----------------------------

    @Test
    void lembreteEnviadoMarcaOEventoComoEnviadoSemTocarNoNotificadorDoCuidador() {
        when(notificadorDoPaciente.notificar(LEMBRETE)).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(service.enviarLembrete(EVENTO_ID, LEMBRETE)).isEqualTo(ResultadoEnvioPush.ENVIADO);
        verify(eventos).marcarEnviado(EVENTO_ID);
        verifyNoInteractions(notificador);
    }

    @Test
    void lembreteComTokenInvalidoSemTokenOuPushDesligadoEncerraOEventoComOMotivo() {
        when(notificadorDoPaciente.notificar(LEMBRETE)).thenReturn(ResultadoEnvioPush.TOKEN_INVALIDO,
                ResultadoEnvioPush.SEM_TOKEN, ResultadoEnvioPush.PUSH_DESLIGADO);

        service.enviarLembrete(1L, LEMBRETE);
        service.enviarLembrete(2L, LEMBRETE);
        service.enviarLembrete(3L, LEMBRETE);

        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.TOKEN_INVALIDO);
        verify(eventos).marcarFalha(2L, MotivoFalhaNotificacao.SEM_TOKEN);
        verify(eventos).marcarFalha(3L, MotivoFalhaNotificacao.PUSH_DESLIGADO);
        verify(eventos, never()).marcarEnviado(anyLong());
    }

    @Test
    void lembreteComFalhaTemporariaOuExcecaoFicaPendenteParaARetentativa() {
        when(notificadorDoPaciente.notificar(LEMBRETE)).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA)
                .thenThrow(new IllegalStateException("FCM fora do ar"));

        assertThat(service.enviarLembrete(EVENTO_ID, LEMBRETE)).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
        assertThat(service.enviarLembrete(EVENTO_ID, LEMBRETE)).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
        verifyNoInteractions(eventos);
    }
}
