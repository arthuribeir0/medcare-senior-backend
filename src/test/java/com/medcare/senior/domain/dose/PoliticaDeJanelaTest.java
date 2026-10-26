package com.medcare.senior.domain.dose;

import com.medcare.senior.domain.configuracao.Tolerancia;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fronteiras da janela (antecedência 60 min, margem depois do aviso 30 min): abre = horário - 60, aviso = horário +
 * tolerância, encerra = aviso + 30. Cada fronteira é conferida exatamente no limite, 1 segundo antes e 1 segundo
 * depois, com as tolerâncias mínima (5), padrão (30) e máxima (120) da configuração do cuidador.
 *
 * <p>Semântica nos limites exatos: em abreEm já pode confirmar; em avisoEm a confirmação ainda é no horário; em
 * encerraEm a confirmação ainda é aceita (com atraso) e a exibição ainda é ATRASADA.
 */
class PoliticaDeJanelaTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 8, 8, 0);
    private static final LocalDateTime ABRE = HORARIO.minusMinutes(60);
    private static final Duration TOLERANCIA = Duration.ofMinutes(30);
    private static final LocalDateTime AVISO = HORARIO.plusMinutes(30);
    private static final LocalDateTime ENCERRA = HORARIO.plusMinutes(60);

    private final PoliticaDeJanela politica = new PoliticaDeJanela(
            Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(30));

    @Test
    void calculaAJanelaAPartirDoHorarioProgramado() {
        assertThat(politica.janela(HORARIO, TOLERANCIA)).isEqualTo(new JanelaDose(ABRE, AVISO, ENCERRA));
        assertThat(politica.abreEm(HORARIO)).isEqualTo(ABRE);
        assertThat(politica.avisoEm(HORARIO, TOLERANCIA)).isEqualTo(AVISO);
        assertThat(politica.encerraEm(HORARIO, TOLERANCIA)).isEqualTo(ENCERRA);
        assertThat(politica.toleranciaPadrao()).isEqualTo(TOLERANCIA);
        assertThat(politica.margemPosAviso()).isEqualTo(Duration.ofMinutes(30));
    }

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void encerraSempreTrintaMinutosDepoisDoAviso(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        assertThat(politica.janela(HORARIO, tolerancia)).isEqualTo(new JanelaDose(ABRE, HORARIO.plusMinutes(minutos),
                HORARIO.plusMinutes(minutos + 30L)));
    }

    // ---- confirmabilidade -----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void confirmabilidadeNaAbertura(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        assertThat(confirmabilidade(ABRE.minusSeconds(1), tolerancia)).isEqualTo(Confirmabilidade.MUITO_CEDO);
        assertThat(confirmabilidade(ABRE, tolerancia)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(ABRE.plusSeconds(1), tolerancia)).isEqualTo(Confirmabilidade.OK);
    }

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void confirmabilidadeNoAvisoContinuaOk(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        LocalDateTime aviso = HORARIO.plusMinutes(minutos);
        assertThat(confirmabilidade(aviso.minusSeconds(1), tolerancia)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(aviso, tolerancia)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(aviso.plusSeconds(1), tolerancia)).isEqualTo(Confirmabilidade.OK);
    }

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void confirmabilidadeNoEncerramento(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        LocalDateTime encerra = HORARIO.plusMinutes(minutos + 30L);
        assertThat(confirmabilidade(encerra.minusSeconds(1), tolerancia)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(encerra, tolerancia)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(encerra.plusSeconds(1), tolerancia)).isEqualTo(Confirmabilidade.ENCERRADA);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = {"CONFIRMADA_NO_HORARIO", "NAO_TOMADA", "CONFIRMADA_COM_ATRASO"})
    void doseRegistradaEhJaRegistradaEmQualquerMomento(StatusDose registrado) {
        assertThat(politica.confirmabilidade(registrado, HORARIO, ABRE.minusHours(1), TOLERANCIA))
                .isEqualTo(Confirmabilidade.JA_REGISTRADA);
        assertThat(politica.confirmabilidade(registrado, HORARIO, HORARIO, TOLERANCIA))
                .isEqualTo(Confirmabilidade.JA_REGISTRADA);
        assertThat(politica.confirmabilidade(registrado, HORARIO, ENCERRA.plusHours(1), TOLERANCIA))
                .isEqualTo(Confirmabilidade.JA_REGISTRADA);
    }

    // ---- status de exibição ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void statusExibicaoNoAviso(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        LocalDateTime aviso = HORARIO.plusMinutes(minutos);
        assertThat(exibicao(aviso.minusSeconds(1), tolerancia)).isEqualTo(StatusExibicao.PENDENTE);
        assertThat(exibicao(aviso, tolerancia)).isEqualTo(StatusExibicao.PENDENTE);
        assertThat(exibicao(aviso.plusSeconds(1), tolerancia)).isEqualTo(StatusExibicao.ATRASADA);
    }

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void statusExibicaoNoEncerramento(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        LocalDateTime encerra = HORARIO.plusMinutes(minutos + 30L);
        assertThat(exibicao(encerra.minusSeconds(1), tolerancia)).isEqualTo(StatusExibicao.ATRASADA);
        assertThat(exibicao(encerra, tolerancia)).isEqualTo(StatusExibicao.ATRASADA);
        assertThat(exibicao(encerra.plusSeconds(1), tolerancia)).isEqualTo(StatusExibicao.NAO_TOMADA);
    }

    @Test
    void statusExibicaoAntesDaAberturaEhPendente() {
        assertThat(exibicao(ABRE.minusHours(5), TOLERANCIA)).isEqualTo(StatusExibicao.PENDENTE);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = {"CONFIRMADA_NO_HORARIO", "NAO_TOMADA", "CONFIRMADA_COM_ATRASO"})
    void doseRegistradaExibeOStatusGravado(StatusDose registrado) {
        assertThat(politica.statusExibicao(registrado, HORARIO, AVISO.plusMinutes(1), TOLERANCIA))
                .isEqualTo(StatusExibicao.valueOf(registrado.name()));
        assertThat(politica.statusExibicao(registrado, HORARIO, ENCERRA.plusMinutes(1), TOLERANCIA))
                .isEqualTo(StatusExibicao.valueOf(registrado.name()));
    }

    // ---- status da confirmação ------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {Tolerancia.MINIMO_MINUTOS, 30, Tolerancia.MAXIMO_MINUTOS})
    void confirmacaoAteOAvisoInclusiveEhNoHorarioDepoisEhComAtraso(int minutos) {
        Duration tolerancia = Duration.ofMinutes(minutos);
        LocalDateTime aviso = HORARIO.plusMinutes(minutos);
        assertThat(politica.statusDaConfirmacao(HORARIO, ABRE, tolerancia)).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso.minusSeconds(1), tolerancia))
                .isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso, tolerancia)).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso.plusSeconds(1), tolerancia))
                .isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(politica.statusDaConfirmacao(HORARIO, politica.encerraEm(HORARIO, tolerancia), tolerancia))
                .isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
    }

    @Test
    void avaliarJuntaJanelaStatusConfirmabilidadeELimite() {
        Dose dose = Dose.pendente(1000L, HORARIO);
        DoseDetalhada detalhe = new DoseDetalhada(dose, 10L, "José", 100L, "Losartana", "50mg", "COMPRIMIDO");

        DoseAvaliada avaliada = politica.avaliar(detalhe, AVISO.plusMinutes(1), TOLERANCIA);

        assertThat(avaliada.detalhe()).isSameAs(detalhe);
        assertThat(avaliada.janela()).isEqualTo(new JanelaDose(ABRE, AVISO, ENCERRA));
        assertThat(avaliada.limiteConfirmacao()).isEqualTo(ENCERRA);
        assertThat(avaliada.statusExibicao()).isEqualTo(StatusExibicao.ATRASADA);
        assertThat(avaliada.confirmabilidade()).isEqualTo(Confirmabilidade.OK);
        assertThat(avaliada.toleranciaMinutos()).isEqualTo(30);
    }

    // ---- tolerância por chamada -----------------------------------------------------------------------------------

    @Test
    void outraToleranciaMoveOAvisoOEncerramentoEOStatusDaConfirmacao() {
        Duration sessenta = Duration.ofMinutes(60);
        LocalDateTime aviso60 = HORARIO.plusMinutes(60);
        LocalDateTime encerra60 = HORARIO.plusMinutes(90);

        assertThat(politica.janela(HORARIO, sessenta)).isEqualTo(new JanelaDose(ABRE, aviso60, encerra60));
        assertThat(politica.statusExibicao(StatusDose.PENDENTE, HORARIO, AVISO.plusMinutes(1), sessenta))
                .isEqualTo(StatusExibicao.PENDENTE);
        assertThat(politica.statusExibicao(StatusDose.PENDENTE, HORARIO, aviso60.plusSeconds(1), sessenta))
                .isEqualTo(StatusExibicao.ATRASADA);
        // Com a padrão (30) a dose já estaria encerrada às 09:01; com 60 ainda pode ser confirmada
        assertThat(politica.confirmabilidade(StatusDose.PENDENTE, HORARIO, ENCERRA.plusMinutes(1), sessenta))
                .isEqualTo(Confirmabilidade.OK);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso60, sessenta)).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso60.plusSeconds(1), sessenta))
                .isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(politica.avaliar(new DoseDetalhada(Dose.pendente(1L, HORARIO), 10L, "José", 100L, "Losartana", "50mg",
                "COMPRIMIDO"), HORARIO, sessenta).toleranciaMinutos()).isEqualTo(60);
    }

    @Test
    void qualquerToleranciaDaConfiguracaoEncerraDepoisDoAviso() {
        // Por construção (encerra = aviso + margem) não existe tolerância que alcance o encerramento
        for (int minutos = Tolerancia.MINIMO_MINUTOS; minutos <= Tolerancia.MAXIMO_MINUTOS; minutos++) {
            Duration tolerancia = Duration.ofMinutes(minutos);
            assertThat(politica.avisoEm(HORARIO, tolerancia)).isBefore(politica.encerraEm(HORARIO, tolerancia));
        }
    }

    @Test
    void toleranciaEfetivaEhADoCuidadorOuAPadrao() {
        assertThat(politica.toleranciaEfetiva(null)).isEqualTo(TOLERANCIA);
        assertThat(politica.toleranciaEfetiva(45)).isEqualTo(Duration.ofMinutes(45));
        assertThat(politica.toleranciaEfetiva(Tolerancia.MAXIMO_MINUTOS)).isEqualTo(Duration.ofMinutes(120));
        assertThatThrownBy(() -> politica.toleranciaEfetiva(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.toleranciaEfetiva(-5)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaToleranciaInvalidaPorChamada() {
        assertThatThrownBy(() -> politica.avisoEm(HORARIO, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.avisoEm(HORARIO, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.encerraEm(HORARIO, Duration.ofMinutes(-1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tolerância deve ser positiva");
    }

    // ---- validação dos parâmetros ---------------------------------------------------------------------------------

    @Test
    void recusaParametrosNaoPositivos() {
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ZERO, Duration.ofMinutes(30), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(-1), Duration.ofMinutes(30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("margem depois do aviso");
    }

    @Test
    void toleranciaPadraoMaiorQueAMargemEhValida() {
        // A regra antiga (tolerância < encerramento fixo) deixou de existir: o encerramento acompanha a tolerância
        PoliticaDeJanela longa = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(200), Duration.ofMinutes(30));
        assertThat(longa.encerraEm(HORARIO, longa.toleranciaPadrao())).isEqualTo(HORARIO.plusMinutes(230));
    }

    private Confirmabilidade confirmabilidade(LocalDateTime agora, Duration tolerancia) {
        return politica.confirmabilidade(StatusDose.PENDENTE, HORARIO, agora, tolerancia);
    }

    private StatusExibicao exibicao(LocalDateTime agora, Duration tolerancia) {
        return politica.statusExibicao(StatusDose.PENDENTE, HORARIO, agora, tolerancia);
    }
}
