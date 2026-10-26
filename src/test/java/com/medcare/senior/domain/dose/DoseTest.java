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

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = {"CONFIRMADA_NO_HORARIO", "CONFIRMADA_COM_ATRASO"})
    void dosePendentePodeSerConfirmadaSemAlterarAOriginal(StatusDose confirmado) {
        LocalDateTime horario = LocalDateTime.of(2026, 10, 8, 8, 0);
        LocalDateTime agora = horario.plusMinutes(40);
        Dose pendente = Dose.builder().id(7L).tratamentoId(1000L).horarioProgramado(horario)
                .status(StatusDose.PENDENTE).build();

        Dose confirmada = pendente.confirmar(confirmado, agora, ConfirmadoPor.PACIENTE);

        assertThat(confirmada).isNotSameAs(pendente);
        assertThat(confirmada.getId()).isEqualTo(7L);
        assertThat(confirmada.getTratamentoId()).isEqualTo(1000L);
        assertThat(confirmada.getHorarioProgramado()).isEqualTo(horario);
        assertThat(confirmada.getStatus()).isEqualTo(confirmado);
        assertThat(confirmada.getHoraConfirmacao()).isEqualTo(agora);
        assertThat(confirmada.getConfirmadoPor()).isEqualTo(ConfirmadoPor.PACIENTE);
        assertThat(pendente.getStatus()).isEqualTo(StatusDose.PENDENTE);
        assertThat(pendente.getHoraConfirmacao()).isNull();
        assertThat(pendente.getConfirmadoPor()).isNull();
    }

    @Test
    void confirmacaoGuardaQuemConfirmouEExigeQueSejaInformado() {
        Dose pendente = Dose.pendente(1000L, LocalDateTime.of(2026, 10, 8, 8, 0));
        LocalDateTime agora = LocalDateTime.of(2026, 10, 8, 8, 5);

        assertThat(pendente.confirmar(StatusDose.CONFIRMADA_NO_HORARIO, agora, ConfirmadoPor.CUIDADOR).getConfirmadoPor())
                .isEqualTo(ConfirmadoPor.CUIDADOR);
        assertThatThrownBy(() -> pendente.confirmar(StatusDose.CONFIRMADA_NO_HORARIO, agora, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quem confirmou");
        // Dose não confirmada não tem quem confirmou
        assertThat(pendente.marcarComoNaoTomada().getConfirmadoPor()).isNull();
    }

    @Test
    void horaDaConfirmacaoEhTruncadaEmMicrossegundosComoNoPostgres() {
        LocalDateTime horario = LocalDateTime.of(2026, 10, 8, 8, 0);
        LocalDateTime agoraComNanos = horario.plusMinutes(5).withNano(123_456_789);
        Dose pendente = Dose.builder().id(7L).tratamentoId(1000L).horarioProgramado(horario)
                .status(StatusDose.PENDENTE).build();

        Dose confirmada = pendente.confirmar(StatusDose.CONFIRMADA_NO_HORARIO, agoraComNanos, ConfirmadoPor.PACIENTE);

        assertThat(confirmada.getHoraConfirmacao()).isEqualTo(horario.plusMinutes(5).withNano(123_456_000));
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = "PENDENTE", mode = EnumSource.Mode.EXCLUDE)
    void doseRegistradaNaoPodeSerConfirmada(StatusDose status) {
        Dose registrada = Dose.builder().id(7L).tratamentoId(1000L)
                .horarioProgramado(LocalDateTime.of(2026, 10, 8, 8, 0)).status(status).build();

        assertThatThrownBy(() -> registrada.confirmar(StatusDose.CONFIRMADA_NO_HORARIO, LocalDateTime.of(2026, 10, 8, 8, 5), ConfirmadoPor.PACIENTE))
                .isInstanceOf(TransicaoDeDoseInvalidaException.class);
    }

    @ParameterizedTest
    @EnumSource(value = StatusDose.class, names = {"PENDENTE", "NAO_TOMADA"})
    void confirmacaoSoGeraNoHorarioOuComAtraso(StatusDose invalido) {
        Dose pendente = Dose.pendente(1000L, LocalDateTime.of(2026, 10, 8, 8, 0));

        assertThatThrownBy(() -> pendente.confirmar(invalido, LocalDateTime.of(2026, 10, 8, 8, 5), ConfirmadoPor.PACIENTE))
                .isInstanceOf(IllegalArgumentException.class);
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
