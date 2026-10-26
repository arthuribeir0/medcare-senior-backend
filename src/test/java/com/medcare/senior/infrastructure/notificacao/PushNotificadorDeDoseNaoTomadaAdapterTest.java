package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificadorDeDoseNaoTomadaAdapterTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 8, 45);
    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 20L;
    private static final String TOKEN = "token-do-aparelho";

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @Mock
    private NotificacaoPushPort notificacaoPush;

    private PushNotificadorDeDoseNaoTomadaAdapter notificador;

    @BeforeEach
    void setUp() {
        notificador = new PushNotificadorDeDoseNaoTomadaAdapter(cuidadorRepository, notificacaoPush,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseNaoTomadaNotificacao notificacao(LocalDateTime horario) {
        return new DoseNaoTomadaNotificacao(10L, CUIDADOR_ID, PACIENTE_ID, "Maria", horario);
    }

    private void cuidadorComToken(String token) {
        when(cuidadorRepository.findById(CUIDADOR_ID))
                .thenReturn(Optional.of(Cuidador.builder().id(CUIDADOR_ID).fcmToken(token).build()));
    }

    @Test
    void enviaMensagemEmPortuguesComPacienteEHorarioSemOMedicamento() {
        cuidadorComToken(TOKEN);
        when(notificacaoPush.enviar(anyString(), anyString(), anyString(), anyMap())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(notificador.notificar(notificacao(AGORA.toLocalDate().atTime(8, 0)))).isEqualTo(ResultadoEnvioPush.ENVIADO);

        // Sem o nome do medicamento (dado de saúde passaria pelo FCM/Google); o payload leva só tipo, IDs e data
        verify(notificacaoPush).enviar(TOKEN, "Dose não confirmada", "Maria ainda não confirmou a dose das 08:00",
                Map.of("tipo", "DOSE_NAO_CONFIRMADA", "doseId", "10", "pacienteId", "20", "data", "2026-10-16"));
        verify(cuidadorRepository, never()).removerFcmTokenSeIgual(anyLong(), anyString());
    }

    @Test
    void doseDeOutroDiaLevaADataNoCorpo() {
        cuidadorComToken(TOKEN);
        when(notificacaoPush.enviar(anyString(), anyString(), anyString(), anyMap())).thenReturn(ResultadoEnvioPush.ENVIADO);

        notificador.notificar(notificacao(LocalDateTime.of(2026, 10, 15, 22, 0)));

        verify(notificacaoPush).enviar(TOKEN, "Dose não confirmada", "Maria ainda não confirmou a dose das 22:00 de 15/10",
                Map.of("tipo", "DOSE_NAO_CONFIRMADA", "doseId", "10", "pacienteId", "20", "data", "2026-10-15"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void cuidadorSemTokenNaoRecebePushEDevolveSemToken(String token) {
        cuidadorComToken(token);

        assertThat(notificador.notificar(notificacao(AGORA.minusHours(1)))).isEqualTo(ResultadoEnvioPush.SEM_TOKEN);

        verifyNoInteractions(notificacaoPush);
    }

    @Test
    void contaExcluidaNaoRecebePushEDevolveSemToken() {
        when(cuidadorRepository.findById(CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThat(notificador.notificar(notificacao(AGORA.minusHours(1)))).isEqualTo(ResultadoEnvioPush.SEM_TOKEN);

        verifyNoInteractions(notificacaoPush);
    }

    @Test
    void tokenInvalidoEhDescartadoDoCuidador() {
        cuidadorComToken(TOKEN);
        when(notificacaoPush.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(ResultadoEnvioPush.TOKEN_INVALIDO);

        assertThat(notificador.notificar(notificacao(AGORA.minusHours(1)))).isEqualTo(ResultadoEnvioPush.TOKEN_INVALIDO);

        // Só apaga se ainda for o mesmo token (o cuidador pode ter registrado outro nesse meio tempo)
        verify(cuidadorRepository).removerFcmTokenSeIgual(CUIDADOR_ID, TOKEN);
    }

    @Test
    void falhaTemporariaNaoDescartaOTokenNemLancaExcecao() {
        cuidadorComToken(TOKEN);
        when(notificacaoPush.enviar(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);

        assertThatCode(() -> assertThat(notificador.notificar(notificacao(AGORA.minusHours(1))))
                .isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA)).doesNotThrowAnyException();

        verify(notificacaoPush).enviar(any(), any(), any(), any());
        verify(cuidadorRepository, never()).removerFcmTokenSeIgual(anyLong(), anyString());
    }

    @Test
    void corpoUsaOHorarioProgramadoEmFormato24Horas() {
        String corpo = notificador.corpo(notificacao(AGORA.toLocalDate().atTime(20, 5)));

        assertThat(corpo).isEqualTo("Maria ainda não confirmou a dose das 20:05");
    }
}
