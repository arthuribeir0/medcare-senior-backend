package com.medcare.senior.domain.dose;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

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
}
