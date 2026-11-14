/*
 * Testes automatizados de lembrar paciente da dose (10 casos). Cobre: cria o lembrete reivindica e envia com o
 * nome do remédio e a validade até o aviso ao cuidador; não envia antes do atraso mínimo de dois minutos; envia
 * exatamente nos dois minutos; não envia no aviso ao cuidador nem depois com a tolerância do cuidador; segunda
 * execução não envia de novo porque o evento já existe; não envia se a retentativa ganhou a primeira tentativa;
 * falha temporária conta como lembrete não enviado e fica para a retentativa; erro em uma dose não impede as
 * demais; entre outros.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.domain.dose.DoseParaLembrete;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.LembreteDoPacienteNotificacao;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.notificacao.TipoEventoNotificacao;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LembrarPacienteDaDoseUseCaseTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 8, 5);
    private static final Duration ATRASO = Duration.ofMinutes(2);
    private static final int LOTE = 500;
    // Tolerância padrão de 30 min: o lembrete de uma dose das 08:00 vale de 08:02 até antes de 08:30
    private static final PoliticaDeJanela POLITICA = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30),
            Duration.ofMinutes(30));
    private static final TipoEventoNotificacao LEMBRETE = TipoEventoNotificacao.LEMBRETE_PACIENTE;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private EventoNotificacaoRepositoryPort eventos;

    @Mock
    private EnvioDeAvisoService envioDeAviso;

    private LembrarPacienteDaDoseUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = useCaseEm(AGORA);
    }

    private LembrarPacienteDaDoseUseCase useCaseEm(LocalDateTime agora) {
        return new LembrarPacienteDaDoseUseCase(doseRepository, eventos, envioDeAviso, POLITICA,
                new LembrarPacienteDaDoseUseCase.Parametros(ATRASO, LOTE), Clock.fixed(agora.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseParaLembrete dose(long id, LocalDateTime horario, Integer toleranciaDoCuidador) {
        return new DoseParaLembrete(id, horario, 2L, "Losartana", toleranciaDoCuidador);
    }

    private void encontradas(LocalDateTime agora, DoseParaLembrete... doses) {
        when(doseRepository.findPendentesParaLembrete(agora, ATRASO, POLITICA.toleranciaPadrao(), LOTE))
                .thenReturn(List.of(doses));
    }

    @Test
    void criaOLembreteReivindicaEEnviaComONomeDoRemedioEAValidadeAteOAvisoAoCuidador() {
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(5), null));
        when(eventos.criarSeAusente(10L, LEMBRETE)).thenReturn(Optional.of(100L));
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviarLembrete(eq(100L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        LembrarPacienteDaDoseUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado.lembretes()).isEqualTo(1);
        assertThat(resultado.enviados()).isEqualTo(1);
        verify(envioDeAviso).enviarLembrete(100L, new LembreteDoPacienteNotificacao(10L, 2L, "Losartana",
                AGORA.minusMinutes(5), AGORA.minusMinutes(5).plusMinutes(30)));
    }

    @Test
    void naoEnviaAntesDoAtrasoMinimoDeDoisMinutos() {
        // Dose das 08:04 às 08:05: só 1 minuto depois do horário (o lembrete do aparelho ainda é a vez)
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(1), null));

        assertThat(useCase.executar().lembretes()).isZero();
        verifyNoInteractions(eventos, envioDeAviso);
    }

    @Test
    void enviaExatamenteNosDoisMinutos() {
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(2), null));
        when(eventos.criarSeAusente(10L, LEMBRETE)).thenReturn(Optional.of(100L));
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviarLembrete(eq(100L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(useCase.executar().enviados()).isEqualTo(1);
    }

    @Test
    void naoEnviaNoAvisoAoCuidadorNemDepoisComAToleranciaDoCuidador() {
        // Cuidador com tolerância de 10 min: dose das 07:55 chega ao aviso às 08:05 (agora); a das 07:56 ainda não
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(10), 10), dose(11L, AGORA.minusMinutes(9), 10));
        when(eventos.criarSeAusente(11L, LEMBRETE)).thenReturn(Optional.of(110L));
        when(eventos.reivindicar(110L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviarLembrete(eq(110L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        assertThat(useCase.executar().lembretes()).isEqualTo(1);
        verify(eventos, never()).criarSeAusente(10L, LEMBRETE);
    }

    @Test
    void segundaExecucaoNaoEnviaDeNovoPorqueOEventoJaExiste() {
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(5), null));
        when(eventos.criarSeAusente(10L, LEMBRETE)).thenReturn(Optional.of(100L), Optional.empty());
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviarLembrete(eq(100L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        useCase.executar();
        LembrarPacienteDaDoseUseCase.Resultado segunda = useCase.executar();

        assertThat(segunda.lembretes()).isZero();
        verify(envioDeAviso).enviarLembrete(eq(100L), any());
    }

    @Test
    void naoEnviaSeARetentativaGanhouAPrimeiraTentativa() {
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(5), null));
        when(eventos.criarSeAusente(10L, LEMBRETE)).thenReturn(Optional.of(100L));
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(false);

        assertThat(useCase.executar().lembretes()).isZero();
        verifyNoInteractions(envioDeAviso);
    }

    @Test
    void falhaTemporariaContaComoLembreteNaoEnviadoEFicaParaARetentativa() {
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(5), null));
        when(eventos.criarSeAusente(10L, LEMBRETE)).thenReturn(Optional.of(100L));
        when(eventos.reivindicar(100L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviarLembrete(eq(100L), any())).thenReturn(ResultadoEnvioPush.FALHA_TEMPORARIA);

        LembrarPacienteDaDoseUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado.lembretes()).isEqualTo(1);
        assertThat(resultado.enviados()).isZero();
        verify(eventos, never()).marcarFalha(anyLong(), any());
    }

    @Test
    void erroEmUmaDoseNaoImpedeAsDemais() {
        encontradas(AGORA, dose(10L, AGORA.minusMinutes(6), null), dose(11L, AGORA.minusMinutes(5), null));
        when(eventos.criarSeAusente(10L, LEMBRETE)).thenThrow(new IllegalStateException("falha simulada"));
        when(eventos.criarSeAusente(11L, LEMBRETE)).thenReturn(Optional.of(110L));
        when(eventos.reivindicar(110L, 0, AGORA)).thenReturn(true);
        when(envioDeAviso.enviarLembrete(eq(110L), any())).thenReturn(ResultadoEnvioPush.ENVIADO);

        LembrarPacienteDaDoseUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(resultado.enviados()).isEqualTo(1);
    }

    @Test
    void nenhumaDoseNaoFazNada() {
        encontradas(AGORA);

        assertThat(useCase.executar()).isEqualTo(new LembrarPacienteDaDoseUseCase.Resultado(0, 0, 0));
        verifyNoInteractions(eventos, envioDeAviso);
    }

    @Test
    void parametrosInvalidosImpedemASubida() {
        assertThatThrownBy(() -> new LembrarPacienteDaDoseUseCase.Parametros(Duration.ofMinutes(-1), 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LembrarPacienteDaDoseUseCase.Parametros(null, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LembrarPacienteDaDoseUseCase.Parametros(ATRASO, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
