package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class LogNotificadorDeDoseNaoTomadaAdapterTest {

    @Test
    void comOFcmDesligadoOAvisoVaiSoParaOLogEContaComoPushDesligado() {
        LogNotificadorDeDoseNaoTomadaAdapter notificador = new LogNotificadorDeDoseNaoTomadaAdapter();

        ResultadoEnvioPush resultado = notificador.notificar(
                new DoseNaoTomadaNotificacao(10L, 1L, 20L, "Maria", LocalDateTime.of(2026, 10, 16, 8, 0)));

        // O aviso não chegou ao cuidador: o evento vira FALHA (PUSH_DESLIGADO), sem retentativa
        assertThat(resultado).isEqualTo(ResultadoEnvioPush.PUSH_DESLIGADO);
    }
}
