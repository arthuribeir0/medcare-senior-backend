package com.medcare.senior.infrastructure.security;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Limita tentativas malsucedidas por IP, em memória: depois de {@value #MAX_FALHAS} falhas dentro de uma janela
 * de 15 minutos (contada a partir da primeira falha), o IP fica bloqueado até o fim dessa janela.
 *
 * <p>Limitações: o estado é perdido quando a aplicação reinicia e não é compartilhado entre instâncias.
 */
@Component
public class RateLimiter {

    static final int MAX_FALHAS = 5;
    static final Duration JANELA = Duration.ofMinutes(15);
    private static final Duration INTERVALO_LIMPEZA = Duration.ofMinutes(1);

    private final ConcurrentHashMap<String, Tentativas> tentativasPorIp = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> ultimaLimpeza = new AtomicReference<>(Instant.now());

    /**
     * @return quantos segundos faltam para o IP ser liberado, ou 0 se ele não estiver bloqueado
     */
    public long segundosAteLiberar(String ip) {
        Instant agora = Instant.now();
        limparExpiradasSeNecessario(agora);

        Tentativas tentativas = tentativasPorIp.get(ip);
        if (tentativas == null || tentativas.expirou(agora) || tentativas.falhas() < MAX_FALHAS) {
            return 0;
        }
        long milisRestantes = Duration.between(agora, tentativas.fimJanela()).toMillis();
        return Math.max(1, (milisRestantes + 999) / 1000);
    }

    public void registrarFalha(String ip) {
        Instant agora = Instant.now();
        tentativasPorIp.compute(ip, (chave, atual) -> atual == null || atual.expirou(agora)
                ? new Tentativas(agora, 1)
                : new Tentativas(atual.inicioJanela(), atual.falhas() + 1));
    }

    private void limparExpiradasSeNecessario(Instant agora) {
        Instant ultima = ultimaLimpeza.get();
        if (agora.isAfter(ultima.plus(INTERVALO_LIMPEZA)) && ultimaLimpeza.compareAndSet(ultima, agora)) {
            tentativasPorIp.values().removeIf(tentativas -> tentativas.expirou(agora));
        }
    }

    private record Tentativas(Instant inicioJanela, int falhas) {

        Instant fimJanela() {
            return inicioJanela.plus(JANELA);
        }

        boolean expirou(Instant agora) {
            return !agora.isBefore(fimJanela());
        }
    }
}
