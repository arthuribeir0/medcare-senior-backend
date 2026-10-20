package com.medcare.senior.domain.relatorio;

import com.medcare.senior.domain.dose.StatusDose;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.medcare.senior.domain.dose.StatusDose.CONFIRMADA_COM_ATRASO;
import static com.medcare.senior.domain.dose.StatusDose.NAO_TOMADA;
import static com.medcare.senior.domain.dose.StatusDose.PENDENTE;
import static com.medcare.senior.domain.dose.StatusDose.TOMADA;
import static org.assertj.core.api.Assertions.assertThat;

class TotaisDoDiaTest {

    @Test
    void diaSemDosesNaoTemAderencia() {
        assertThat(TotaisDoDia.de(List.of())).isEqualTo(new TotaisDoDia(0, 0, 0, 0, 0, null));
    }

    @Test
    void soPendentesNaoTemAderencia() {
        assertThat(TotaisDoDia.de(List.of(PENDENTE, PENDENTE))).isEqualTo(new TotaisDoDia(2, 0, 0, 0, 2, null));
    }

    @Test
    void misturaDeStatusContaCadaUmEAderenciaIgnoraPendentes() {
        List<StatusDose> status = List.of(TOMADA, TOMADA, CONFIRMADA_COM_ATRASO, NAO_TOMADA, PENDENTE, PENDENTE);

        // (2 + 1) / (6 - 2) = 75,0%
        assertThat(TotaisDoDia.de(status)).isEqualTo(new TotaisDoDia(6, 2, 1, 1, 2, 75.0));
    }

    @Test
    void aderenciaArredondaAUmaCasaParaCima() {
        assertThat(TotaisDoDia.de(List.of(TOMADA, NAO_TOMADA, NAO_TOMADA)).aderenciaPercentual()).isEqualTo(33.3);
        assertThat(TotaisDoDia.de(List.of(TOMADA, CONFIRMADA_COM_ATRASO, NAO_TOMADA)).aderenciaPercentual()).isEqualTo(66.7);
        assertThat(TotaisDoDia.de(List.of(TOMADA, NAO_TOMADA, NAO_TOMADA, NAO_TOMADA, NAO_TOMADA, NAO_TOMADA,
                NAO_TOMADA, NAO_TOMADA)).aderenciaPercentual()).isEqualTo(12.5);
        assertThat(TotaisDoDia.de(List.of(NAO_TOMADA)).aderenciaPercentual()).isZero();
        assertThat(TotaisDoDia.de(List.of(TOMADA, PENDENTE)).aderenciaPercentual()).isEqualTo(100.0);
    }

    @Test
    void contagensDaoOMesmoResultadoQueAListaDeStatus() {
        assertThat(TotaisDoDia.deContagens(1, 1, 1, 1))
                .isEqualTo(TotaisDoDia.de(List.of(TOMADA, CONFIRMADA_COM_ATRASO, NAO_TOMADA, PENDENTE)));
        assertThat(TotaisDoDia.deContagens(7, 0, 1, 0).aderenciaPercentual()).isEqualTo(87.5);
        assertThat(TotaisDoDia.deContagens(2, 0, 1, 0).aderenciaPercentual()).isEqualTo(66.7);
        assertThat(TotaisDoDia.deContagens(0, 0, 0, 3)).isEqualTo(new TotaisDoDia(3, 0, 0, 0, 3, null));
    }
}
