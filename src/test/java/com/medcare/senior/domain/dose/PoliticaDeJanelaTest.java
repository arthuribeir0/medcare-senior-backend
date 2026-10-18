package com.medcare.senior.domain.dose;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Fronteiras da janela (antecedência 60 min, tolerância 30 min, encerramento 180 min): exatamente nos limites,
 * 1 segundo antes e 1 segundo depois. A tolerância é informada por chamada (padrão 30 min aqui).
 */
class PoliticaDeJanelaTest {

    private static final LocalDateTime HORARIO = LocalDateTime.of(2026, 10, 8, 8, 0);
    private static final LocalDateTime ABRE = HORARIO.minusMinutes(60);
    private static final LocalDateTime AVISO = HORARIO.plusMinutes(30);
    private static final LocalDateTime ENCERRA = HORARIO.plusMinutes(180);
    private static final Duration TOLERANCIA = Duration.ofMinutes(30);

    private final PoliticaDeJanela politica = new PoliticaDeJanela(
            Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(180));

    @Test
    void calculaAJanelaAPartirDoHorarioProgramado() {
        assertThat(politica.janela(HORARIO, TOLERANCIA)).isEqualTo(new JanelaDose(ABRE, AVISO, ENCERRA));
        assertThat(politica.abreEm(HORARIO)).isEqualTo(ABRE);
        assertThat(politica.avisoEm(HORARIO, TOLERANCIA)).isEqualTo(AVISO);
        assertThat(politica.encerraEm(HORARIO)).isEqualTo(ENCERRA);
        assertThat(politica.toleranciaPadrao()).isEqualTo(TOLERANCIA);
    }

    // ---- confirmabilidade -----------------------------------------------------------------------------------------

    @Test
    void confirmabilidadeNaAbertura() {
        assertThat(confirmabilidade(ABRE.minusSeconds(1))).isEqualTo(Confirmabilidade.MUITO_CEDO);
        assertThat(confirmabilidade(ABRE)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(ABRE.plusSeconds(1))).isEqualTo(Confirmabilidade.OK);
    }

    @Test
    void confirmabilidadeNoEncerramento() {
        assertThat(confirmabilidade(ENCERRA.minusSeconds(1))).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(ENCERRA)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(ENCERRA.plusSeconds(1))).isEqualTo(Confirmabilidade.ENCERRADA);
    }

    @Test
    void confirmabilidadeNoAvisoContinuaOk() {
        assertThat(confirmabilidade(AVISO)).isEqualTo(Confirmabilidade.OK);
        assertThat(confirmabilidade(AVISO.plusSeconds(1))).isEqualTo(Confirmabilidade.OK);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = {"TOMADA", "NAO_TOMADA", "CONFIRMADA_COM_ATRASO"})
    void doseRegistradaEhJaRegistradaEmQualquerMomento(StatusDose registrado) {
        assertThat(politica.confirmabilidade(registrado, HORARIO, ABRE.minusHours(1))).isEqualTo(Confirmabilidade.JA_REGISTRADA);
        assertThat(politica.confirmabilidade(registrado, HORARIO, HORARIO)).isEqualTo(Confirmabilidade.JA_REGISTRADA);
        assertThat(politica.confirmabilidade(registrado, HORARIO, ENCERRA.plusHours(1))).isEqualTo(Confirmabilidade.JA_REGISTRADA);
    }

    // ---- status de exibição ---------------------------------------------------------------------------------------

    @Test
    void statusExibicaoNoAviso() {
        assertThat(exibicao(AVISO.minusSeconds(1))).isEqualTo(StatusExibicao.PENDENTE);
        assertThat(exibicao(AVISO)).isEqualTo(StatusExibicao.PENDENTE);
        assertThat(exibicao(AVISO.plusSeconds(1))).isEqualTo(StatusExibicao.ATRASADA);
    }

    @Test
    void statusExibicaoNoEncerramento() {
        assertThat(exibicao(ENCERRA.minusSeconds(1))).isEqualTo(StatusExibicao.ATRASADA);
        assertThat(exibicao(ENCERRA)).isEqualTo(StatusExibicao.ATRASADA);
        assertThat(exibicao(ENCERRA.plusSeconds(1))).isEqualTo(StatusExibicao.NAO_TOMADA);
    }

    @Test
    void statusExibicaoAntesDaAberturaEhPendente() {
        assertThat(exibicao(ABRE.minusHours(5))).isEqualTo(StatusExibicao.PENDENTE);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = {"TOMADA", "NAO_TOMADA", "CONFIRMADA_COM_ATRASO"})
    void doseRegistradaExibeOStatusGravado(StatusDose registrado) {
        assertThat(politica.statusExibicao(registrado, HORARIO, AVISO.plusMinutes(1), TOLERANCIA))
                .isEqualTo(StatusExibicao.valueOf(registrado.name()));
        assertThat(politica.statusExibicao(registrado, HORARIO, ENCERRA.plusMinutes(1), TOLERANCIA))
                .isEqualTo(StatusExibicao.valueOf(registrado.name()));
    }

    // ---- status da confirmação ------------------------------------------------------------------------------------

    @Test
    void confirmacaoAteOAvisoInclusiveEhTomadaDepoisEhComAtraso() {
        assertThat(politica.statusDaConfirmacao(HORARIO, ABRE, TOLERANCIA)).isEqualTo(StatusDose.TOMADA);
        assertThat(politica.statusDaConfirmacao(HORARIO, AVISO.minusSeconds(1), TOLERANCIA)).isEqualTo(StatusDose.TOMADA);
        assertThat(politica.statusDaConfirmacao(HORARIO, AVISO, TOLERANCIA)).isEqualTo(StatusDose.TOMADA);
        assertThat(politica.statusDaConfirmacao(HORARIO, AVISO.plusSeconds(1), TOLERANCIA)).isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(politica.statusDaConfirmacao(HORARIO, ENCERRA, TOLERANCIA)).isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
    }

    @Test
    void avaliarJuntaJanelaStatusEConfirmabilidade() {
        Dose dose = Dose.pendente(1000L, HORARIO);
        DoseDetalhada detalhe = new DoseDetalhada(dose, 10L, "José", 100L, "Losartana", "50mg", "COMPRIMIDO");

        DoseAvaliada avaliada = politica.avaliar(detalhe, AVISO.plusMinutes(1), TOLERANCIA);

        assertThat(avaliada.detalhe()).isSameAs(detalhe);
        assertThat(avaliada.janela()).isEqualTo(new JanelaDose(ABRE, AVISO, ENCERRA));
        assertThat(avaliada.statusExibicao()).isEqualTo(StatusExibicao.ATRASADA);
        assertThat(avaliada.confirmabilidade()).isEqualTo(Confirmabilidade.OK);
        assertThat(avaliada.toleranciaMinutos()).isEqualTo(30);
    }

    // ---- tolerância por chamada -----------------------------------------------------------------------------------

    @Test
    void outraToleranciaMoveSoOAvisoEOStatusDaConfirmacao() {
        Duration sessenta = Duration.ofMinutes(60);
        LocalDateTime aviso60 = HORARIO.plusMinutes(60);

        assertThat(politica.janela(HORARIO, sessenta)).isEqualTo(new JanelaDose(ABRE, aviso60, ENCERRA));
        assertThat(politica.statusExibicao(StatusDose.PENDENTE, HORARIO, AVISO.plusMinutes(1), sessenta))
                .isEqualTo(StatusExibicao.PENDENTE);
        assertThat(politica.statusExibicao(StatusDose.PENDENTE, HORARIO, aviso60.plusSeconds(1), sessenta))
                .isEqualTo(StatusExibicao.ATRASADA);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso60, sessenta)).isEqualTo(StatusDose.TOMADA);
        assertThat(politica.statusDaConfirmacao(HORARIO, aviso60.plusSeconds(1), sessenta))
                .isEqualTo(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(politica.avaliar(new DoseDetalhada(Dose.pendente(1L, HORARIO), 10L, "José", 100L, "Losartana", "50mg",
                "COMPRIMIDO"), HORARIO, sessenta).toleranciaMinutos()).isEqualTo(60);
    }

    @Test
    void toleranciaMaximaDaConfiguracaoCabeAntesDoEncerramento() {
        // A configuração do cuidador vai de 5 a 120 minutos; o encerramento (180) precisa ficar sempre depois
        assertThat(politica.avisoEm(HORARIO, Duration.ofMinutes(120))).isBefore(politica.encerraEm(HORARIO));
        assertThat(politica.avisoEm(HORARIO, Duration.ofMinutes(5))).isEqualTo(HORARIO.plusMinutes(5));
    }

    @Test
    void recusaToleranciaInvalidaPorChamada() {
        assertThatThrownBy(() -> politica.avisoEm(HORARIO, Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.avisoEm(HORARIO, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> politica.janela(HORARIO, Duration.ofMinutes(180)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tolerância deve ser menor");
    }

    // ---- validação dos parâmetros ---------------------------------------------------------------------------------

    @Test
    void recusaParametrosNaoPositivos() {
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ZERO, Duration.ofMinutes(30), Duration.ofMinutes(180)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(-1), Duration.ofMinutes(180)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recusaToleranciaMaiorOuIgualAoEncerramento() {
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(180), Duration.ofMinutes(180)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tolerância deve ser menor");
        assertThatThrownBy(() -> new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(200), Duration.ofMinutes(180)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private Confirmabilidade confirmabilidade(LocalDateTime agora) {
        return politica.confirmabilidade(StatusDose.PENDENTE, HORARIO, agora);
    }

    private StatusExibicao exibicao(LocalDateTime agora) {
        return politica.statusExibicao(StatusDose.PENDENTE, HORARIO, agora, TOLERANCIA);
    }
}
