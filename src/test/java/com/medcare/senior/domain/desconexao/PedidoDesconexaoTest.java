package com.medcare.senior.domain.desconexao;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class PedidoDesconexaoTest {

    private static final LocalDateTime CRIADO = LocalDateTime.of(2026, 11, 9, 14, 30);
    private static final PedidoDesconexao PEDIDO = PedidoDesconexao.novo(10L, CRIADO, Duration.ofHours(24));

    @Test
    void novoNasceePendenteComPrazoDaValidade() {
        assertThat(PEDIDO.status()).isEqualTo(StatusPedidoDesconexao.PENDENTE);
        assertThat(PEDIDO.expiraEm()).isEqualTo(CRIADO.plusHours(24));
        assertThat(PEDIDO.decididoEm()).isNull();
        assertThat(PEDIDO.decididoPorCuidadorId()).isNull();
    }

    @Test
    void continuaPendenteAte23h59EVenceExatamenteEm24h() {
        assertThat(PEDIDO.statusEm(CRIADO.plusHours(23).plusMinutes(59))).isEqualTo(StatusPedidoDesconexao.PENDENTE);
        assertThat(PEDIDO.pendenteEm(CRIADO.plusHours(24).minusNanos(1))).isTrue();
        assertThat(PEDIDO.statusEm(CRIADO.plusHours(24))).isEqualTo(StatusPedidoDesconexao.EXPIRADO);
        assertThat(PEDIDO.pendenteEm(CRIADO.plusHours(24))).isFalse();
    }

    @Test
    void vistoDepoisDoPrazoSaiExpiradoComODecididoEmNoPrazo() {
        PedidoDesconexao visto = PEDIDO.vistoEm(CRIADO.plusDays(2));

        assertThat(visto.status()).isEqualTo(StatusPedidoDesconexao.EXPIRADO);
        assertThat(visto.decididoEm()).isEqualTo(PEDIDO.expiraEm());
        assertThat(PEDIDO.vistoEm(CRIADO.plusHours(1))).isSameAs(PEDIDO);
    }

    @Test
    void decididoNaoMudaComOTempo() {
        PedidoDesconexao recusado = new PedidoDesconexao(1L, 10L, StatusPedidoDesconexao.RECUSADO, CRIADO,
                CRIADO.plusHours(24), CRIADO.plusMinutes(5), 1L);

        assertThat(recusado.statusEm(CRIADO.plusDays(3))).isEqualTo(StatusPedidoDesconexao.RECUSADO);
        assertThat(recusado.vistoEm(CRIADO.plusDays(3))).isSameAs(recusado);
    }
}
