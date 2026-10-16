package com.medcare.senior.domain.dose;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DoseTest {

    @Test
    void doseNovaNasceComoPendenteSemConfirmacao() {
        LocalDateTime horario = LocalDateTime.of(2026, 10, 8, 20, 0);

        Dose dose = Dose.pendente(1000L, horario);

        assertThat(dose.getId()).isNull();
        assertThat(dose.getTratamentoId()).isEqualTo(1000L);
        assertThat(dose.getHorarioProgramado()).isEqualTo(horario);
        assertThat(dose.getHoraConfirmacao()).isNull();
        assertThat(dose.getStatus()).isEqualTo(StatusDose.PENDENTE);
        assertThat(dose.isRegistrada()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void statusDiferenteDePendenteEhRegistrado(StatusDose status) {
        Dose dose = Dose.builder().tratamentoId(1000L).horarioProgramado(LocalDateTime.now()).status(status).build();

        assertThat(dose.isRegistrada()).isTrue();
    }

    @Test
    void dosePendentePodeSerMarcadaComoNaoTomadaSemAlterarAOriginal() {
        LocalDateTime horario = LocalDateTime.of(2026, 10, 8, 8, 0);
        Dose pendente = Dose.builder().id(7L).tratamentoId(1000L).horarioProgramado(horario)
                .status(StatusDose.PENDENTE).build();

        Dose naoTomada = pendente.marcarComoNaoTomada();

        assertThat(naoTomada).isNotSameAs(pendente);
        assertThat(naoTomada.getId()).isEqualTo(7L);
        assertThat(naoTomada.getTratamentoId()).isEqualTo(1000L);
        assertThat(naoTomada.getHorarioProgramado()).isEqualTo(horario);
        assertThat(naoTomada.getStatus()).isEqualTo(StatusDose.NAO_TOMADA);
        assertThat(naoTomada.getHoraConfirmacao()).isNull();
        assertThat(naoTomada.isRegistrada()).isTrue();
        // A original continua PENDENTE: Dose é imutável
        assertThat(pendente.getStatus()).isEqualTo(StatusDose.PENDENTE);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void doseRegistradaNaoPodeSerMarcadaComoNaoTomada(StatusDose status) {
        Dose registrada = Dose.builder().id(7L).tratamentoId(1000L)
                .horarioProgramado(LocalDateTime.of(2026, 10, 8, 8, 0)).status(status).build();

        assertThatThrownBy(registrada::marcarComoNaoTomada)
                .isInstanceOf(TransicaoDeDoseInvalidaException.class)
                .hasMessageContaining(status.name());
    }
}
