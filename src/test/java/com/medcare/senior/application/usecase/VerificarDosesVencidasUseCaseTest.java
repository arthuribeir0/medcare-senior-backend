package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.DoseEncerrada;
import com.medcare.senior.domain.dose.DoseParaAviso;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VerificarDosesVencidasUseCaseTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 14, 0);
    private static final Duration TOLERANCIA = Duration.ofMinutes(30);
    private static final Duration MARGEM = Duration.ofMinutes(30);
    private static final int LOTE = 500;
    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 2L;

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private EventoNotificacaoRepositoryPort eventos;

    @Mock
    private EnvioDeAvisoService envioDeAviso;

    private VerificarDosesVencidasUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = criar(LOTE);
    }

    private VerificarDosesVencidasUseCase criar(int lote) {
        return new VerificarDosesVencidasUseCase(doseRepository, eventos, envioDeAviso,
                new PoliticaDeJanela(Duration.ofMinutes(60), TOLERANCIA, MARGEM),
                new VerificarDosesVencidasUseCase.Parametros(lote), cacheDeRelatorio,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseParaAviso paraAviso(long doseId, LocalDateTime horario) {
        return new DoseParaAviso(doseId, horario, CUIDADOR_ID, IDOSO_ID, "Maria");
    }

    private void semEncerradas() {
        when(doseRepository.encerrarVencidas(AGORA, TOLERANCIA, MARGEM, LOTE)).thenReturn(List.of());
    }

    private void semAvisos() {
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE)).thenReturn(List.of());
    }

    // ---- parâmetros das buscas ----------------------------------------------------------------------------------

    @Test
    void encerraEAvisaComAToleranciaPadraoEAMargemDepoisDoAviso() {
        semEncerradas();
        semAvisos();

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 0, 0, 0));
        // O banco aplica a tolerância de cada cuidador (ou a padrão) mais a margem, linha a linha
        verify(doseRepository).encerrarVencidas(AGORA, TOLERANCIA, MARGEM, LOTE);
        verify(doseRepository).findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE);
        verify(doseRepository, never()).registrarSePendente(any());
        verifyNoInteractions(eventos, envioDeAviso);
    }

    // ---- fase de encerramento -----------------------------------------------------------------------------------

    @Test
    void contaAsDosesQueOUpdateAtomicoEncerrouSemAviso() {
        // Também cobre a dose que chegou ao encerramento sem ter sido avisada (aplicação parada): encerra sem push.
        // Dose confirmada antes do UPDATE não volta na lista (perdeu a corrida para a confirmação) e não conta.
        when(doseRepository.encerrarVencidas(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(new DoseEncerrada(10L, IDOSO_ID), new DoseEncerrada(11L, 3L),
                        new DoseEncerrada(12L, IDOSO_ID)));
        semAvisos();

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(3, 0, 0, 0));
        // Os relatórios diários dos dois idosos em cache são descartados
        verify(cacheDeRelatorio).descartarDoIdoso(IDOSO_ID);
        verify(cacheDeRelatorio).descartarDoIdoso(3L);
        // Nenhuma leitura seguida de gravação: o encerramento é um comando só no repositório
        verify(doseRepository, never()).registrarSePendente(any());
        verifyNoInteractions(eventos, envioDeAviso);
    }

    @Test
    void erroNoEncerramentoNaoImpedeOAviso() {
        when(doseRepository.encerrarVencidas(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenThrow(new IllegalStateException("falha simulada"));
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(paraAviso(20L, AGORA.minusMinutes(40))));
        when(eventos.criarSeAusente(20L)).thenReturn(Optional.of(200L));
        when(eventos.reivindicar(200L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviar(eq(200L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 1, 1, 1));
    }

    // ---- fase de aviso ------------------------------------------------------------------------------------------

    @Test
    void doseQuePassouDaToleranciaGeraEventoEAvisoEContinuaPendente() {
        LocalDateTime horario = AGORA.minusMinutes(31);
        semEncerradas();
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(paraAviso(10L, horario)));
        when(eventos.criarSeAusente(10L)).thenReturn(Optional.of(100L));
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviar(eq(100L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 1, 1, 0));
        verify(envioDeAviso).enviar(100L, new DoseNaoTomadaNotificacao(10L, CUIDADOR_ID, IDOSO_ID, "Maria", horario));
        // RN-009/RN-014: o aviso NÃO muda a dose; ela só vira NAO_TOMADA no encerramento
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void doseQueJaTinhaEventoNaoEhAvisadaDeNovo() {
        // Outra execução (ou instância) criou o evento entre a busca e o INSERT: ON CONFLICT DO NOTHING
        semEncerradas();
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(paraAviso(10L, AGORA.minusMinutes(40))));
        when(eventos.criarSeAusente(10L)).thenReturn(Optional.empty());

        assertThat(useCase.executar()).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 0, 0, 0));
        verify(eventos, never()).reivindicar(anyLong(), eq(0), any());
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void eventoCujaPrimeiraTentativaFoiReivindicadaPorOutroNaoEhEnviado() {
        // A retentativa pegou o evento recém-criado (tentativas = 0) antes desta execução: quem reivindicou envia
        semEncerradas();
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(paraAviso(10L, AGORA.minusMinutes(40))));
        when(eventos.criarSeAusente(10L)).thenReturn(Optional.of(100L));
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(false);

        assertThat(useCase.executar()).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 0, 0, 0));
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void falhaDeEntregaContaComoAvisoNaoEnviadoSemErro() {
        semEncerradas();
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(paraAviso(10L, AGORA.minusMinutes(40)), paraAviso(11L, AGORA.minusMinutes(35)),
                        paraAviso(12L, AGORA.minusMinutes(32))));
        when(eventos.criarSeAusente(anyLong())).thenAnswer(chamada -> Optional.of((Long) chamada.getArgument(0) * 10));
        when(eventos.reivindicar(anyLong(), eq(0), eq(AGORA))).thenReturn(true);
        when(envioDeAviso.enviar(eq(100L), any())).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);
        when(envioDeAviso.enviar(eq(110L), any())).thenReturn(ResultadoEnvioPush.SEM_TOKEN);
        when(envioDeAviso.enviar(eq(120L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        // Três avisos tentados, um aceito; falha de entrega não é erro da varredura (o evento registra o motivo)
        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 3, 1, 0));
    }

    @Test
    void erroInesperadoEmUmAvisoNaoInterrompeOsDemais() {
        semEncerradas();
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, LOTE))
                .thenReturn(List.of(paraAviso(10L, AGORA.minusMinutes(40)), paraAviso(11L, AGORA.minusMinutes(35))));
        when(eventos.criarSeAusente(10L)).thenThrow(new IllegalStateException("falha simulada"));
        when(eventos.criarSeAusente(11L)).thenReturn(Optional.of(110L));
        when(eventos.reivindicar(110L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviar(eq(110L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        VerificarDosesVencidasUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new VerificarDosesVencidasUseCase.Resultado(0, 1, 1, 1));
        verify(envioDeAviso).enviar(eq(110L), argThat(n -> n.doseId().equals(11L)));
    }

    // ---- parâmetros ---------------------------------------------------------------------------------------------

    @Test
    void asDuasFasesRespeitamOLoteMaximoConfigurado() {
        VerificarDosesVencidasUseCase comLoteDe2 = criar(2);
        when(doseRepository.encerrarVencidas(AGORA, TOLERANCIA, MARGEM, 2)).thenReturn(List.of());
        when(doseRepository.findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, 2)).thenReturn(List.of());

        comLoteDe2.executar();

        verify(doseRepository).encerrarVencidas(AGORA, TOLERANCIA, MARGEM, 2);
        verify(doseRepository).findPendentesParaAviso(AGORA, TOLERANCIA, MARGEM, 2);
    }

    @Test
    void loteMenorQueUmEhRejeitado() {
        assertThatThrownBy(() -> new VerificarDosesVencidasUseCase.Parametros(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
