/*
 * Testes automatizados de reenviar avisos pendentes (16 casos). Cobre: sem eventos não faz nada; reivindica rele a
 * dose e reenvia; dose já registrada cancela o aviso sem push; dose sem dono cancela o aviso; dose confirmada
 * entre a busca e a reivindicação cancela na releitura; reivindicação perdida não envia; evento nunca tentado é
 * enviado na hora; falha temporária antes da última tentativa deixa o evento pendente; entre outros.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoParaRetentativa;
import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.notificacao.TipoEventoNotificacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReenviarAvisosPendentesUseCaseTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 14, 0);
    private static final LocalDateTime HORARIO = AGORA.minusMinutes(50);
    private static final int MAX = 5;
    private static final Duration BASE = Duration.ofMinutes(5);
    private static final int LOTE = 200;
    // Tolerância padrão de 30 min: o lembrete ao paciente de uma dose das 13:40 vale até 14:10
    private static final PoliticaDeJanela POLITICA = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30),
            Duration.ofMinutes(30));

    @Mock
    private EventoNotificacaoRepositoryPort eventos;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private EnvioDeAvisoService envioDeAviso;

    private ReenviarAvisosPendentesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReenviarAvisosPendentesUseCase(eventos, doseRepository, envioDeAviso, POLITICA,
                new ReenviarAvisosPendentesUseCase.Parametros(MAX, BASE, LOTE),
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static EventoParaRetentativa evento(long eventoId, int tentativas, StatusDose statusDose) {
        return new EventoParaRetentativa(eventoId, TipoEventoNotificacao.AVISO_CUIDADOR, tentativas, eventoId * 10,
                statusDose, HORARIO, false, 1L, 2L, "Maria", "Losartana", null);
    }

    private void elegiveis(EventoParaRetentativa... lista) {
        when(eventos.findParaRetentativa(AGORA, MAX, BASE, LOTE)).thenReturn(List.of(lista));
    }

    @Test
    void semEventosNaoFazNada() {
        elegiveis();

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 0, 0, 0));
        verify(eventos).findParaRetentativa(AGORA, MAX, BASE, LOTE);
        verifyNoInteractions(envioDeAviso, doseRepository);
    }

    @Test
    void reivindicaReleADoseEReenvia() {
        elegiveis(evento(1L, 2, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 2, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviar(eq(1L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 1, 0, 0, 0));
        verify(envioDeAviso).enviar(1L, new DoseNaoTomadaNotificacao(10L, 1L, 2L, "Maria", HORARIO));
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void doseJaRegistradaCancelaOAvisoSemPush(StatusDose status) {
        elegiveis(evento(1L, 1, status));

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 1, 0, 0));
        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.DOSE_JA_REGISTRADA);
        verify(eventos, never()).reivindicar(anyLong(), anyInt(), any());
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void doseSemDonoCancelaOAviso() {
        elegiveis(new EventoParaRetentativa(1L, TipoEventoNotificacao.AVISO_CUIDADOR, 1, 10L, StatusDose.PENDENTE, HORARIO,
                true, null, null, null, null, null));

        assertThat(useCase.executar().canceladas()).isEqualTo(1);
        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.DOSE_JA_REGISTRADA);
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void doseConfirmadaEntreABuscaEAReivindicacaoCancelaNaReleitura() {
        elegiveis(evento(1L, 1, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 1, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.CONFIRMADA_COM_ATRASO));

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 1, 0, 0));
        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.DOSE_JA_REGISTRADA);
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void reivindicacaoPerdidaNaoEnvia() {
        // Outro job reivindicou a mesma tentativa antes (tentativas já não é mais 2)
        elegiveis(evento(1L, 2, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 2, AGORA)).thenReturn(false);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 0, 0, 0));
        verifyNoInteractions(envioDeAviso, doseRepository);
    }

    @Test
    void eventoNuncaTentadoEhEnviadoNaHora() {
        // tentativas = 0: a varredura caiu entre o INSERT do evento e o envio
        elegiveis(evento(1L, 0, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 0, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviar(eq(1L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(useCase.executar().enviadas()).isEqualTo(1);
    }

    @Test
    void falhaTemporariaAntesDaUltimaTentativaDeixaOEventoPendente() {
        elegiveis(evento(1L, 3, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 3, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviar(eq(1L), any())).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 0, 0, 0, 0));
        verify(eventos, never()).marcarFalha(anyLong(), any());
    }

    @Test
    void falhaTemporariaNaQuintaTentativaEsgotaOAviso() {
        elegiveis(evento(1L, 4, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 4, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviar(eq(1L), any())).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 0, 0, 1, 0));
        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.TENTATIVAS_ESGOTADAS);
    }

    @Test
    void tokenInvalidoNaUltimaTentativaNaoViraEsgotado() {
        // O EnvioDeAvisoService já encerrou o evento como TOKEN_INVALIDO (e o notificador limpou o token)
        elegiveis(evento(1L, 4, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 4, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviar(eq(1L), any())).thenReturn(ResultadoEnvioPush.TOKEN_INVALIDO);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 0, 0, 0, 0));
        verify(eventos, never()).marcarFalha(anyLong(), any());
    }

    @Test
    void eventoComTentativasJaEsgotadasEhEncerrado() {
        // A instância caiu depois de reivindicar a 5ª tentativa e antes de registrar o resultado
        elegiveis(evento(1L, 5, StatusDose.PENDENTE));

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 0, 1, 0));
        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.TENTATIVAS_ESGOTADAS);
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void erroEmUmEventoNaoInterrompeOsDemais() {
        elegiveis(evento(1L, 1, StatusDose.PENDENTE), evento(2L, 1, StatusDose.PENDENTE));
        when(eventos.reivindicar(1L, 1, AGORA)).thenThrow(new IllegalStateException("banco fora do ar"));
        when(eventos.reivindicar(2L, 1, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(20L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviar(eq(2L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 1, 0, 0, 1));
    }

    @Test
    void parametrosInvalidosSaoRejeitados() {
        assertThatThrownBy(() -> new ReenviarAvisosPendentesUseCase.Parametros(0, BASE, LOTE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReenviarAvisosPendentesUseCase.Parametros(MAX, Duration.ofSeconds(30), LOTE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReenviarAvisosPendentesUseCase.Parametros(MAX, null, LOTE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ReenviarAvisosPendentesUseCase.Parametros(MAX, BASE, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- lembrete ao paciente: mesmo caminho, até o aviso ao cuidador ----------------------------------------------

    private static EventoParaRetentativa lembrete(long eventoId, StatusDose statusDose, LocalDateTime horario,
                                                  Integer toleranciaDoCuidador) {
        return new EventoParaRetentativa(eventoId, TipoEventoNotificacao.LEMBRETE_PACIENTE, 1, eventoId * 10, statusDose,
                horario, false, 1L, 2L, "Maria", "Losartana", toleranciaDoCuidador);
    }

    @Test
    void lembreteDentroDaJanelaEhReenviadoPeloNotificadorDoPaciente() {
        // Dose das 13:50 com a tolerância padrão (30): o lembrete vale até 14:20
        LocalDateTime horario = AGORA.minusMinutes(10);
        elegiveis(lembrete(1L, StatusDose.PENDENTE, horario, null));
        when(eventos.reivindicar(1L, 1, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviarLembrete(eq(1L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 1, 0, 0, 0));
        verify(envioDeAviso).enviarLembrete(1L, new LembreteDoPacienteNotificacao(10L, 2L, "Losartana", horario,
                horario.plusMinutes(30)));
        verify(envioDeAviso, never()).enviar(anyLong(), any());
    }

    @Test
    void lembreteQueChegouAoAvisoAoCuidadorEhEncerradoForaDaJanelaSemPush() {
        // Dose das 13:10 com tolerância padrão: o aviso ao cuidador foi às 13:40; com a do cuidador (50), é agora
        elegiveis(lembrete(1L, StatusDose.PENDENTE, HORARIO, null), lembrete(2L, StatusDose.PENDENTE, HORARIO, 50));

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 2, 0, 0));
        verify(eventos).marcarFalha(1L, MotivoFalhaNotificacao.FORA_DA_JANELA);
        verify(eventos).marcarFalha(2L, MotivoFalhaNotificacao.FORA_DA_JANELA);
        verify(eventos, never()).reivindicar(anyLong(), anyInt(), any());
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void lembreteComAToleranciaMaiorDoCuidadorAindaValeEDoseConfirmadaCancela() {
        // Tolerância do cuidador de 60: a dose das 13:10 ainda está no lembrete até 14:10
        elegiveis(lembrete(1L, StatusDose.PENDENTE, HORARIO, 60),
                lembrete(2L, StatusDose.CONFIRMADA_NO_HORARIO, HORARIO, 60));
        when(eventos.reivindicar(1L, 1, AGORA)).thenReturn(true);
        when(doseRepository.findStatusById(10L)).thenReturn(Optional.of(StatusDose.PENDENTE));
        when(envioDeAviso.enviarLembrete(eq(1L), any())).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);

        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(1, 0, 1, 0, 0));
        verify(eventos).marcarFalha(2L, MotivoFalhaNotificacao.DOSE_JA_REGISTRADA);
        verify(eventos, never()).marcarFalha(1L, MotivoFalhaNotificacao.FORA_DA_JANELA);
    }
}
