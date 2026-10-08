package com.medcare.senior.domain.tratamento;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class TratamentoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 8);

    private static Tratamento comDataFim(LocalDate dataFim) {
        return Tratamento.builder().dataInicio(HOJE.minusDays(30)).dataFim(dataFim).build();
    }

    @Test
    void tratamentoSemDataFimEstaAtivo() {
        assertThat(comDataFim(null).isAtivo(HOJE)).isTrue();
    }

    @Test
    void tratamentoQueTerminaHojeAindaEstaAtivo() {
        assertThat(comDataFim(HOJE).isAtivo(HOJE)).isTrue();
    }

    @Test
    void tratamentoQueTerminaNoFuturoEstaAtivo() {
        assertThat(comDataFim(HOJE.plusDays(1)).isAtivo(HOJE)).isTrue();
    }

    @Test
    void tratamentoQueTerminouOntemNaoEstaAtivo() {
        assertThat(comDataFim(HOJE.minusDays(1)).isAtivo(HOJE)).isFalse();
    }

    @Test
    void tratamentoRemovidoNaoEstaAtivoMesmoSemDataFim() {
        Tratamento removido = comDataFim(null);
        removido.setRemovidoEm(HOJE.minusDays(1).atStartOfDay());

        assertThat(removido.isRemovido()).isTrue();
        assertThat(removido.isAtivo(HOJE)).isFalse();
    }
}
