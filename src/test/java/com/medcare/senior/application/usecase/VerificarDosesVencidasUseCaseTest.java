package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.DoseVencida;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificarDosesVencidasUseCaseTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 14, 0);
    private static final Duration TOLERANCIA = Duration.ofMinutes(30);
    private static final Duration JANELA = Duration.ofHours(24);
    private static final int LOTE = 500;
    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private NotificadorDeDoseNaoTomadaPort notificador;

    private VerificarDosesVencidasUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new VerificarDosesVencidasUseCase(doseRepository, notificador,
                new VerificarDosesVencidasUseCase.Parametros(TOLERANCIA, JANELA, LOTE),
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseVencida vencida(long doseId, LocalDateTime horario) {
        Dose dose = Dose.builder().id(doseId).tratamentoId(100L).horarioProgramado(horario)
                .status(StatusDose.PENDENTE).build();
        return new DoseVencida(dose, CUIDADOR_ID, "Maria", "Losartana");
    }

    private static Dose naoTomadaComId(long doseId) {
        return argThat(dose -> dose != null && dose.getId() == doseId && dose.getStatus() == StatusDose.NAO_TOMADA);
    }

    @Test
    void buscaSomenteDosesCujoHorarioMaisToleranciaJaPassou() {
        when(doseRepository.findPendentesVencidas(any(), eq(LOTE))).thenReturn(List.of());

        useCase.executar();

        // Vencida = horário + 30 min <= 14:00, ou seja, horário <= 13:30. Uma dose das 13:31 está dentro da
        // tolerância e não é buscada.
        verify(doseRepository).findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE);
    }

    @Test
    void doseDentroDaToleranciaNaoEhMarcada() {
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE)).thenReturn(List.of());

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 0, 0, 0));
        verify(doseRepository, never()).registrarSePendente(any());
        verifyNoInteractions(notificador);
    }

    @Test
    void doseForaDaToleranciaEhMarcadaComoNaoTomadaENotificada() {
        LocalDateTime horario = AGORA.minusMinutes(31);
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, horario)));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(1, 1, 1, 0));

        ArgumentCaptor<Dose> gravada = ArgumentCaptor.forClass(Dose.class);
        verify(doseRepository).registrarSePendente(gravada.capture());
        assertThat(gravada.getValue().getId()).isEqualTo(10L);
        assertThat(gravada.getValue().getStatus()).isEqualTo(StatusDose.NAO_TOMADA);
        assertThat(gravada.getValue().getHoraConfirmacao()).isNull();

        verify(notificador).notificar(new DoseNaoTomadaNotificacao(10L, CUIDADOR_ID, "Maria", "Losartana", horario));
    }

    @Test
    void doseVencidaAlemDaJanelaEhMarcadaMasNaoNotificada() {
        // Venceu às 13:59 de ontem (13:29 + 30 min): há mais de 24 h
        LocalDateTime horario = AGORA.minus(JANELA).minus(TOLERANCIA).minusMinutes(1);
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, horario)));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(1, 1, 0, 0));
        verify(doseRepository).registrarSePendente(naoTomadaComId(10L));
        verifyNoInteractions(notificador);
    }

    @Test
    void doseQueVenceuExatamenteNoLimiteDaJanelaNaoEhNotificada() {
        // Vencimento (horário + tolerância) = agora - 24 h: não é "há menos de 24 h"
        LocalDateTime horario = AGORA.minus(JANELA).minus(TOLERANCIA);
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, horario)));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        useCase.executar();

        verifyNoInteractions(notificador);
    }

    @Test
    void doseQueVenceuUmMinutoDentroDaJanelaEhNotificada() {
        LocalDateTime horario = AGORA.minus(JANELA).minus(TOLERANCIA).plusMinutes(1);
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, horario)));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        useCase.executar();

        verify(notificador).notificar(any());
    }

    @Test
    void doseQuePerdeuACorridaNaoEhNotificada() {
        // O UPDATE condicionado a PENDENTE não mudou nada: a dose foi confirmada entre a busca e a gravação
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, AGORA.minusHours(1))));
        when(doseRepository.registrarSePendente(any())).thenReturn(0);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(1, 0, 0, 0));
        verifyNoInteractions(notificador);
    }

    @Test
    void erroAoGravarUmaDoseNaoInterrompeAsDemais() {
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, AGORA.minusHours(2)), vencida(11L, AGORA.minusHours(1))));
        when(doseRepository.registrarSePendente(naoTomadaComId(10L))).thenThrow(new IllegalStateException("falha simulada"));
        when(doseRepository.registrarSePendente(naoTomadaComId(11L))).thenReturn(1);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(2, 1, 1, 1));
        verify(notificador).notificar(argThat(n -> n.doseId().equals(11L)));
        verify(notificador, never()).notificar(argThat(n -> n.doseId().equals(10L)));
    }

    @Test
    void erroAoNotificarUmaDoseNaoInterrompeAsDemais() {
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), LOTE))
                .thenReturn(List.of(vencida(10L, AGORA.minusHours(2)), vencida(11L, AGORA.minusHours(1))));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);
        doThrow(new IllegalStateException("falha simulada"))
                .when(notificador).notificar(argThat(n -> n.doseId().equals(10L)));
        doNothing().when(notificador).notificar(argThat(n -> n.doseId().equals(11L)));

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        // A dose 10 continua marcada (a gravação já aconteceu); só o aviso falhou
        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(2, 2, 1, 1));
        verify(doseRepository).registrarSePendente(naoTomadaComId(11L));
        verify(notificador).notificar(argThat(n -> n.doseId().equals(11L)));
    }

    @Test
    void buscaRespeitaOLoteMaximoConfigurado() {
        VerificarDosesVencidasUseCase comLoteDe2 = new VerificarDosesVencidasUseCase(doseRepository, notificador,
                new VerificarDosesVencidasUseCase.Parametros(TOLERANCIA, JANELA, 2),
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
        when(doseRepository.findPendentesVencidas(AGORA.minus(TOLERANCIA), 2))
                .thenReturn(List.of(vencida(10L, AGORA.minusHours(2)), vencida(11L, AGORA.minusHours(1))));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        VerificarDosesVencidasUseCase.Resultado resultado = comLoteDe2.executar();

        verify(doseRepository).findPendentesVencidas(AGORA.minus(TOLERANCIA), 2);
        assertThat(resultado.vencidas()).isEqualTo(2);
        assertThat(resultado.marcadas()).isEqualTo(2);
    }

    @Test
    void parametrosInvalidosSaoRejeitados() {
        assertThatThrownBy(() -> new VerificarDosesVencidasUseCase.Parametros(Duration.ofMinutes(-1), JANELA, LOTE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificarDosesVencidasUseCase.Parametros(TOLERANCIA, Duration.ZERO, LOTE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new VerificarDosesVencidasUseCase.Parametros(TOLERANCIA, JANELA, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
