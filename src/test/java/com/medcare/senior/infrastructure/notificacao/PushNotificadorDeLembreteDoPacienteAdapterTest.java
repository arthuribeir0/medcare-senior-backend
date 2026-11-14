/*
 * Testes automatizados de push notificador de lembrete do paciente adapter (8 casos). Cobre: envia hora do remédio
 * com o nome o horário o canal do lembrete a tag da dose e a validade; dose de outro dia leva a data; validade
 * nunca fica abaixo de um minuto; sem token não envia nada; token em branco conta como sem token; token inválido
 * apaga o token do paciente só se ainda for o mesmo; outros resultados voltam como vieram sem apagar o token; sem
 * fcm o lembrete vai só para o log com push desligado.
 */
package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.OpcoesDeEntrega;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificadorDeLembreteDoPacienteAdapterTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 8, 2);
    private static final Long PACIENTE_ID = 20L;
    private static final String TOKEN = "token-do-celular-do-paciente";

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private NotificacaoPushPort notificacaoPush;

    private PushNotificadorDeLembreteDoPacienteAdapter notificador;

    @BeforeEach
    void setUp() {
        notificador = new PushNotificadorDeLembreteDoPacienteAdapter(pacienteRepository, notificacaoPush,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static LembreteDoPacienteNotificacao lembrete(LocalDateTime horario, LocalDateTime validoAte) {
        return new LembreteDoPacienteNotificacao(10L, PACIENTE_ID, "Losartana", horario, validoAte);
    }

    private static final LembreteDoPacienteNotificacao DAS_OITO = lembrete(AGORA.minusMinutes(2), AGORA.plusMinutes(28));

    @Test
    void enviaHoraDoRemedioComONomeOHorarioOCanalDoLembreteATagDaDoseEAValidade() {
        when(pacienteRepository.findTokenFcm(PACIENTE_ID)).thenReturn(Optional.of(TOKEN));
        when(notificacaoPush.enviar(eq(TOKEN), anyString(), anyString(), any(), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(notificador.notificar(DAS_OITO)).isEqualTo(ResultadoEnvioPush.ENVIADO);

        ArgumentCaptor<OpcoesDeEntrega> opcoes = ArgumentCaptor.forClass(OpcoesDeEntrega.class);
        verify(notificacaoPush).enviar(eq(TOKEN), eq("Hora do remédio"), eq("Tomar Losartana às 08:00"),
                eq(Map.of("tipo", "LEMBRETE_DOSE", "doseId", "10", "pacienteId", "20", "data", "2026-10-16")),
                opcoes.capture());
        // Mesmo canal e mesma tag do 1º lembrete local da dose: o Android substitui em vez de empilhar
        assertThat(opcoes.getValue()).isEqualTo(new OpcoesDeEntrega("lembrete-dose", "dose-10-1", true,
                Duration.ofMinutes(28)));
        verify(pacienteRepository, never()).removerTokenFcmSeIgual(anyLong(), anyString());
    }

    @Test
    void doseDeOutroDiaLevaAData() {
        assertThat(notificador.corpo(lembrete(LocalDateTime.of(2026, 10, 15, 23, 58), AGORA)))
                .isEqualTo("Tomar Losartana às 23:58 de 15/10");
    }

    @Test
    void validadeNuncaFicaAbaixoDeUmMinuto() {
        assertThat(notificador.opcoes(lembrete(AGORA.minusMinutes(29), AGORA.plusSeconds(10))).validade())
                .isEqualTo(Duration.ofMinutes(1));
    }

    @Test
    void semTokenNaoEnviaNada() {
        when(pacienteRepository.findTokenFcm(PACIENTE_ID)).thenReturn(Optional.empty());

        assertThat(notificador.notificar(DAS_OITO)).isEqualTo(ResultadoEnvioPush.SEM_TOKEN);
        verifyNoInteractions(notificacaoPush);
    }

    @Test
    void tokenEmBrancoContaComoSemToken() {
        when(pacienteRepository.findTokenFcm(PACIENTE_ID)).thenReturn(Optional.of("  "));

        assertThat(notificador.notificar(DAS_OITO)).isEqualTo(ResultadoEnvioPush.SEM_TOKEN);
        verifyNoInteractions(notificacaoPush);
    }

    @Test
    void tokenInvalidoApagaOTokenDoPacienteSoSeAindaForOMesmo() {
        when(pacienteRepository.findTokenFcm(PACIENTE_ID)).thenReturn(Optional.of(TOKEN));
        when(notificacaoPush.enviar(eq(TOKEN), anyString(), anyString(), any(), any()))
                .thenReturn(ResultadoEnvioPush.TOKEN_INVALIDO);

        assertThat(notificador.notificar(DAS_OITO)).isEqualTo(ResultadoEnvioPush.TOKEN_INVALIDO);
        verify(pacienteRepository).removerTokenFcmSeIgual(PACIENTE_ID, TOKEN);
    }

    @ParameterizedTest
    @EnumSource(value = ResultadoEnvioPush.class, names = {"FALHA_TEMPORARIA", "PUSH_DESLIGADO", "SEM_TOKEN"})
    void outrosResultadosVoltamComoVieramSemApagarOToken(ResultadoEnvioPush resultado) {
        when(pacienteRepository.findTokenFcm(PACIENTE_ID)).thenReturn(Optional.of(TOKEN));
        when(notificacaoPush.enviar(eq(TOKEN), anyString(), anyString(), any(), any())).thenReturn(resultado);

        assertThat(notificador.notificar(DAS_OITO)).isEqualTo(resultado);
        verify(pacienteRepository, never()).removerTokenFcmSeIgual(anyLong(), anyString());
    }

    @Test
    void semFcmOLembreteVaiSoParaOLogComPushDesligado() {
        assertThat(new LogNotificadorDeLembreteDoPacienteAdapter().notificar(DAS_OITO))
                .isEqualTo(ResultadoEnvioPush.PUSH_DESLIGADO);
    }
}
