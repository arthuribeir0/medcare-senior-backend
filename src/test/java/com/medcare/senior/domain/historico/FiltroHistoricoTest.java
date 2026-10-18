package com.medcare.senior.domain.historico;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FiltroHistoricoTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 18);

    @Test
    void semFimValeHojeEPaginacaoPadrao() {
        // RN-011: sem data de fim, o sistema assume a data corrente
        FiltroHistorico filtro = FiltroHistorico.de(null, null, null, null, null, null, null, HOJE);

        assertThat(filtro).isEqualTo(new FiltroHistorico(null, null, null, HOJE, null, 0, 20));
    }

    @Test
    void guardaOsFiltrosInformados() {
        FiltroHistorico filtro = FiltroHistorico.de(10L, 100L, HOJE.minusDays(7), HOJE.minusDays(1),
                StatusDose.NAO_TOMADA, 2, 50, HOJE);

        assertThat(filtro).isEqualTo(new FiltroHistorico(10L, 100L, HOJE.minusDays(7), HOJE.minusDays(1),
                StatusDose.NAO_TOMADA, 2, 50));
    }

    @Test
    void inicioDepoisDoFimEhRecusado() {
        // RN-011: a data de início não pode ser posterior à data de fim
        assertThatThrownBy(() -> FiltroHistorico.de(null, null, HOJE.minusDays(1), HOJE.minusDays(2), null, 0, 20, HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("A data de início não pode ser posterior à data de fim")
                .extracting(e -> ((ParametroInvalidoException) e).getCampo()).isEqualTo("de");
        // Sem fim, vale hoje: início no futuro também é recusado
        assertThatThrownBy(() -> FiltroHistorico.de(null, null, HOJE.plusDays(1), null, null, 0, 20, HOJE))
                .isInstanceOf(ParametroInvalidoException.class);
    }

    @Test
    void inicioIgualAoFimEhPermitido() {
        assertThat(FiltroHistorico.de(null, null, HOJE, HOJE, null, 0, 20, HOJE).de()).isEqualTo(HOJE);
    }

    @Test
    void paginaNegativaEhRecusada() {
        assertThatThrownBy(() -> FiltroHistorico.de(null, null, null, null, null, -1, 20, HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting(e -> ((ParametroInvalidoException) e).getCampo()).isEqualTo("page");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 101, -5})
    void tamanhoForaDe1a100EhRecusado(int tamanho) {
        assertThatThrownBy(() -> FiltroHistorico.de(null, null, null, null, null, 0, tamanho, HOJE))
                .isInstanceOf(ParametroInvalidoException.class)
                .extracting(e -> ((ParametroInvalidoException) e).getCampo()).isEqualTo("size");
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void tamanhoNosLimitesEhAceito(int tamanho) {
        assertThat(FiltroHistorico.de(null, null, null, null, null, 0, tamanho, HOJE).tamanho()).isEqualTo(tamanho);
    }

    @Test
    void paginaCalculaOTotalDePaginasEConverte() {
        assertThat(new Pagina<>(List.of(), 0, 20, 0).totalPaginas()).isZero();
        assertThat(new Pagina<>(List.of(), 0, 20, 20).totalPaginas()).isEqualTo(1);
        assertThat(new Pagina<>(List.of(), 0, 20, 57).totalPaginas()).isEqualTo(3);
        Pagina<String> convertida = new Pagina<>(List.of(1, 2), 1, 2, 5).map(String::valueOf);
        assertThat(convertida).isEqualTo(new Pagina<>(List.of("1", "2"), 1, 2, 5));
    }
}
