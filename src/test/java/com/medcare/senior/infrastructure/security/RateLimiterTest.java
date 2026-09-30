package com.medcare.senior.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final String IP = "10.0.0.1";
    private static final String OUTRO_IP = "10.0.0.2";

    private RelogioControlavel relogio;
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        relogio = new RelogioControlavel(Instant.parse("2026-01-01T10:00:00Z"));
        rateLimiter = new RateLimiter(relogio);
    }

    @Test
    void naoDeveBloquearAntesDeAtingirOLimite() {
        registrarFalhas(IP, RateLimiter.MAX_FALHAS - 1);

        assertThat(rateLimiter.segundosAteLiberar(IP)).isZero();
    }

    @Test
    void deveBloquearASextaTentativaAposCincoFalhas() {
        registrarFalhas(IP, 5);

        assertThat(rateLimiter.segundosAteLiberar(IP)).isEqualTo(RateLimiter.JANELA.toSeconds());
    }

    @Test
    void falhasDeUmIpNaoDevemAfetarOutroIp() {
        registrarFalhas(IP, 5);

        assertThat(rateLimiter.segundosAteLiberar(IP)).isPositive();
        assertThat(rateLimiter.segundosAteLiberar(OUTRO_IP)).isZero();
    }

    @Test
    void deveLiberarIpQuandoAJanelaTerminar() {
        registrarFalhas(IP, 5);

        relogio.avancar(RateLimiter.JANELA.minusSeconds(1));
        assertThat(rateLimiter.segundosAteLiberar(IP)).isEqualTo(1);

        relogio.avancar(Duration.ofSeconds(1));
        assertThat(rateLimiter.segundosAteLiberar(IP)).isZero();
    }

    @Test
    void limpezaDeveRemoverSomenteIpsComJanelaExpirada() {
        registrarFalhas(IP, 3);
        relogio.avancar(Duration.ofMinutes(10));
        registrarFalhas(OUTRO_IP, 5);
        assertThat(rateLimiter.ipsMonitorados()).isEqualTo(2);

        // 16 minutos após a primeira falha: a janela do primeiro IP acabou, a do segundo não
        relogio.avancar(Duration.ofMinutes(6));
        rateLimiter.segundosAteLiberar("192.168.0.1"); // qualquer consulta dispara a limpeza

        assertThat(rateLimiter.ipsMonitorados()).isEqualTo(1);
        assertThat(rateLimiter.segundosAteLiberar(OUTRO_IP)).isPositive();
    }

    private void registrarFalhas(String ip, int quantidade) {
        for (int i = 0; i < quantidade; i++) {
            rateLimiter.registrarFalha(ip);
        }
    }

    private static final class RelogioControlavel extends Clock {

        private Instant agora;

        RelogioControlavel(Instant inicio) {
            this.agora = inicio;
        }

        void avancar(Duration duracao) {
            agora = agora.plus(duracao);
        }

        @Override
        public Instant instant() {
            return agora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
