package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.ConfirmadoPor;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.dose.StatusExibicao;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Janela com a tolerância padrão: abre 60 min antes, aviso 30 min depois e encerra 30 min depois do aviso (08:00 ->
 * 07:00, 08:30 e 09:00). Com a tolerância do cuidador, o aviso e o encerramento acompanham.
 */
@ExtendWith(MockitoExtension.class)
class ConfirmarDoseUseCaseTest {

    private static final Long IDOSO_ID = 10L;
    private static final Long CUIDADOR_ID = 1L;
    private static final Long DOSE_ID = 5000L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 8, 8, 0);
    private static final PoliticaDeJanela POLITICA = new PoliticaDeJanela(
            Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(30));

    @Mock
    private CacheDeRelatorioDiarioPort cacheDeRelatorio;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    private ConfirmarDoseUseCase useCaseEm(LocalDateTime agora) {
        return new ConfirmarDoseUseCase(doseRepository, idosoRepository, POLITICA, cacheDeRelatorio,
                Clock.fixed(agora.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseDetalhada detalhe(StatusDose status) {
        Dose dose = Dose.builder().id(DOSE_ID).tratamentoId(1000L).horarioProgramado(HORARIO).status(status).build();
        return new DoseDetalhada(dose, IDOSO_ID, "José", 100L, "Losartana", "50mg", "COMPRIMIDO");
    }

    private void idosoExisteComDose(StatusDose status) {
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).build()));
        when(doseRepository.findDetalhadaByIdAndIdosoId(DOSE_ID, IDOSO_ID)).thenReturn(Optional.of(detalhe(status)));
    }

    private Dose doseGravada() {
        ArgumentCaptor<Dose> captor = ArgumentCaptor.forClass(Dose.class);
        verify(doseRepository).registrarSePendente(captor.capture());
        return captor.getValue();
    }

    @Test
    void noPrazoGravaConfirmadaNoHorarioComAHoraDoServidor() {
        LocalDateTime agora = HORARIO.plusMinutes(5);
        idosoExisteComDose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        DoseAvaliada resultado = useCaseEm(agora).executar(DOSE_ID, IDOSO_ID);

        Dose gravada = doseGravada();
        assertThat(gravada.getId()).isEqualTo(DOSE_ID);
        assertThat(gravada.getStatus()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(gravada.getHoraConfirmacao()).isEqualTo(agora);
        assertThat(gravada.getConfirmadoPor()).isEqualTo(ConfirmadoPor.IDOSO);
        assertThat(resultado.detalhe().dose().getStatus()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(resultado.detalhe().dose().getConfirmadoPor()).isEqualTo(ConfirmadoPor.IDOSO);
        assertThat(resultado.statusExibicao()).isEqualTo(StatusExibicao.CONFIRMADA_NO_HORARIO);
        assertThat(resultado.confirmabilidade()).isEqualTo(Confirmabilidade.JA_REGISTRADA);
        assertThat(resultado.detalhe().medicamentoNome()).isEqualTo("Losartana");
        // Gravada: o relatório diário do idoso em cache é descartado
        verify(cacheDeRelatorio).descartarDoIdoso(IDOSO_ID);
    }

    @Test
    void usaAToleranciaDoCuidadorParaDecidirSeFoiComAtraso() {
        // 40 min depois: com a padrão (30) seria com atraso; o cuidador configurou 60, então é CONFIRMADA_NO_HORARIO
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).build()));
        Dose dose = Dose.builder().id(DOSE_ID).tratamentoId(1000L).horarioProgramado(HORARIO).status(StatusDose.PENDENTE).build();
        when(doseRepository.findDetalhadaByIdAndIdosoId(DOSE_ID, IDOSO_ID)).thenReturn(Optional.of(
                new DoseDetalhada(dose, IDOSO_ID, "José", 100L, "Losartana", "50mg", "COMPRIMIDO", 60)));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        DoseAvaliada resultado = useCaseEm(HORARIO.plusMinutes(40)).executar(DOSE_ID, IDOSO_ID);

        assertThat(doseGravada().getStatus()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(resultado.toleranciaMinutos()).isEqualTo(60);
        assertThat(resultado.detalhe().toleranciaMinutosDoCuidador()).isEqualTo(60);
    }

    @Test
    void exatamenteNoAvisoAindaEhNoHorario() {
        idosoExisteComDose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        useCaseEm(HORARIO.plusMinutes(30)).executar(DOSE_ID, IDOSO_ID);

        assertThat(doseGravada().getStatus()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
    }

    @Test
    void umSegundoDepoisDoAvisoEhConfirmadaComAtraso() {
        LocalDateTime agora = HORARIO.plusMinutes(30).plusSeconds(1);
        idosoExisteComDose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        DoseAvaliada resultado = useCaseEm(agora).executar(DOSE_ID, IDOSO_ID);

        assertThat(doseGravada().getStatus()).isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(resultado.statusExibicao()).isEqualTo(StatusExibicao.CONFIRMADA_COM_ATRASO);
    }

    @Test
    void antesDaAberturaEhMuitoCedo() {
        idosoExisteComDose(StatusDose.PENDENTE);

        assertThatThrownBy(() -> useCaseEm(HORARIO.minusMinutes(60).minusSeconds(1)).executar(DOSE_ID, IDOSO_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.MUITO_CEDO));
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void exatamenteNoEncerramentoAindaConfirmaComAtraso() {
        idosoExisteComDose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        DoseAvaliada resultado = useCaseEm(HORARIO.plusMinutes(60)).executar(DOSE_ID, IDOSO_ID);

        assertThat(doseGravada().getStatus()).isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(resultado.limiteConfirmacao()).isEqualTo(HORARIO.plusMinutes(60));
    }

    @Test
    void oEncerramentoAcompanhaAToleranciaDoCuidador() {
        // Cuidador com 120 min: às 10:30 (= 08:00 + 120 + 30) ainda confirma; com a padrão já estaria encerrada
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).build()));
        Dose dose = Dose.builder().id(DOSE_ID).tratamentoId(1000L).horarioProgramado(HORARIO).status(StatusDose.PENDENTE).build();
        when(doseRepository.findDetalhadaByIdAndIdosoId(DOSE_ID, IDOSO_ID)).thenReturn(Optional.of(
                new DoseDetalhada(dose, IDOSO_ID, "José", 100L, "Losartana", "50mg", "COMPRIMIDO", 120)));
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        assertThatThrownBy(() -> useCaseEm(HORARIO.plusMinutes(150).plusSeconds(1)).executar(DOSE_ID, IDOSO_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.ENCERRADA));
        DoseAvaliada resultado = useCaseEm(HORARIO.plusMinutes(150)).executar(DOSE_ID, IDOSO_ID);

        assertThat(doseGravada().getStatus()).isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(resultado.limiteConfirmacao()).isEqualTo(HORARIO.plusMinutes(150));
    }

    @Test
    void depoisDoEncerramentoEhEncerrada() {
        idosoExisteComDose(StatusDose.PENDENTE);

        assertThatThrownBy(() -> useCaseEm(HORARIO.plusMinutes(60).plusSeconds(1)).executar(DOSE_ID, IDOSO_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.ENCERRADA));
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void doseJaRegistradaNaoMuda() {
        idosoExisteComDose(StatusDose.CONFIRMADA_NO_HORARIO);

        assertThatThrownBy(() -> useCaseEm(HORARIO).executar(DOSE_ID, IDOSO_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.JA_REGISTRADA));
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void doseDeOutroIdosoOuInexistenteEhNaoEncontrada() {
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).build()));
        when(doseRepository.findDetalhadaByIdAndIdosoId(DOSE_ID, IDOSO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCaseEm(HORARIO).executar(DOSE_ID, IDOSO_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Dose não encontrada");
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void idosoQueNaoExisteMaisTemSessaoInvalida() {
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCaseEm(HORARIO).executar(DOSE_ID, IDOSO_ID)).isInstanceOf(SessaoInvalidaException.class);
        verifyNoInteractions(doseRepository);
    }

    @Test
    void perderACorridaNoUpdateEhJaRegistrada() {
        idosoExisteComDose(StatusDose.PENDENTE);
        // Outra transação registrou a dose entre a leitura e o UPDATE condicional
        when(doseRepository.registrarSePendente(any())).thenReturn(0);

        assertThatThrownBy(() -> useCaseEm(HORARIO).executar(DOSE_ID, IDOSO_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.JA_REGISTRADA));
    }

    // ---- confirmação pelo cuidador --------------------------------------------------------------------------------

    private void cuidadorTemADose(StatusDose status) {
        when(doseRepository.findDetalhadaByIdAndCuidadorId(DOSE_ID, CUIDADOR_ID)).thenReturn(Optional.of(detalhe(status)));
    }

    @Test
    void cuidadorConfirmaDoseDeIdosoDeleComAsMesmasRegrasEGravaQuemConfirmou() {
        LocalDateTime agora = HORARIO.plusMinutes(10);
        cuidadorTemADose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        DoseAvaliada resultado = useCaseEm(agora).executarComoCuidador(DOSE_ID, CUIDADOR_ID);

        Dose gravada = doseGravada();
        assertThat(gravada.getStatus()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(gravada.getConfirmadoPor()).isEqualTo(ConfirmadoPor.CUIDADOR);
        assertThat(gravada.getHoraConfirmacao()).isEqualTo(agora);
        assertThat(resultado.detalhe().dose().getConfirmadoPor()).isEqualTo(ConfirmadoPor.CUIDADOR);
        assertThat(resultado.confirmabilidade()).isEqualTo(Confirmabilidade.JA_REGISTRADA);
        verify(cacheDeRelatorio).descartarDoIdoso(IDOSO_ID);
        // O cuidador não passa pelo cadastro do idoso: a dose já vem filtrada pelo dono
        verifyNoInteractions(idosoRepository);
    }

    @Test
    void cuidadorDepoisDoAvisoConfirmaComAtraso() {
        cuidadorTemADose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(1);

        useCaseEm(HORARIO.plusMinutes(45)).executarComoCuidador(DOSE_ID, CUIDADOR_ID);

        assertThat(doseGravada().getStatus()).isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
    }

    @Test
    void cuidadorNaoConfirmaDoseDeIdosoDeOutroCuidadorEOErroEhIgualADoseInexistente() {
        when(doseRepository.findDetalhadaByIdAndCuidadorId(DOSE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCaseEm(HORARIO).executarComoCuidador(DOSE_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Dose não encontrada");
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void cuidadorForaDaJanelaOuComDoseJaRegistradaRecebeOMesmoMotivoDoIdoso() {
        cuidadorTemADose(StatusDose.PENDENTE);
        assertThatThrownBy(() -> useCaseEm(HORARIO.minusMinutes(61)).executarComoCuidador(DOSE_ID, CUIDADOR_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.MUITO_CEDO));
        assertThatThrownBy(() -> useCaseEm(HORARIO.plusMinutes(60).plusSeconds(1)).executarComoCuidador(DOSE_ID, CUIDADOR_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.ENCERRADA));

        cuidadorTemADose(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThatThrownBy(() -> useCaseEm(HORARIO).executarComoCuidador(DOSE_ID, CUIDADOR_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.JA_REGISTRADA));
        verify(doseRepository, never()).registrarSePendente(any());
    }

    @Test
    void cuidadorQuePerdeACorridaRecebeJaRegistrada() {
        // O idoso (ou o job) gravou a dose entre a leitura e o UPDATE do cuidador
        cuidadorTemADose(StatusDose.PENDENTE);
        when(doseRepository.registrarSePendente(any())).thenReturn(0);

        assertThatThrownBy(() -> useCaseEm(HORARIO).executarComoCuidador(DOSE_ID, CUIDADOR_ID))
                .isInstanceOfSatisfying(DoseNaoConfirmavelException.class,
                        e -> assertThat(e.getMotivo()).isEqualTo(Confirmabilidade.JA_REGISTRADA));
        // Nada mudou por esta chamada: o cache fica como está
        verifyNoInteractions(cacheDeRelatorio);
    }
}
