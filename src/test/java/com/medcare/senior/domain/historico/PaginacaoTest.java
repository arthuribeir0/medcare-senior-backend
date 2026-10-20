package com.medcare.senior.domain.historico;

import com.medcare.senior.domain.exception.ParametroInvalidoException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginacaoTest {

    @Test
    void semValoresUsaPagina0ETamanho20() {
        assertThat(Paginacao.de(null, null)).isEqualTo(new Paginacao(0, 20));
    }

    @Test
    void aceitaOsLimites() {
        assertThat(Paginacao.de(0, 1)).isEqualTo(new Paginacao(0, 1));
        assertThat(Paginacao.de(7, 100)).isEqualTo(new Paginacao(7, 100));
    }

    @Test
    void recusaPaginaNegativaETamanhoForaDe1a100NoCampo() {
        assertThatThrownBy(() -> Paginacao.de(-1, 20)).isInstanceOf(ParametroInvalidoException.class)
                .extracting("campo").isEqualTo("page");
        assertThatThrownBy(() -> Paginacao.de(0, 0)).isInstanceOf(ParametroInvalidoException.class)
                .extracting("campo").isEqualTo("size");
        assertThatThrownBy(() -> Paginacao.de(0, 101)).isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("O tamanho da página deve ficar entre 1 e 100");
    }
}
