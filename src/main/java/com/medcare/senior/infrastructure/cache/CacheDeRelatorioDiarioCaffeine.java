package com.medcare.senior.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import com.medcare.senior.domain.relatorio.RelatorioDiario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Relatório diário em memória (Caffeine) por app.cache.relatorio-diario-segundos (padrão 30 s), no máximo
 * {@value #MAXIMO_DE_ENTRADAS} relatórios. Cada instância do backend tem o seu: com uma instância só (a implantação
 * atual) o descarte é imediato; com várias, uma instância pode mostrar o relatório antigo até expirar.
 *
 * <p>Descarte dentro de uma transação: vale na hora e de novo depois do commit, para uma leitura que aconteça entre o
 * descarte e o commit não deixar no cache o relatório de antes da alteração.
 */
@Component
public class CacheDeRelatorioDiarioCaffeine implements CacheDeRelatorioDiarioPort {

    static final int MAXIMO_DE_ENTRADAS = 10_000;

    private final Cache<Chave, RelatorioDiario> cache;

    @Autowired
    public CacheDeRelatorioDiarioCaffeine(@Value("${app.cache.relatorio-diario-segundos:30}") long segundos) {
        this(Duration.ofSeconds(segundos), Ticker.systemTicker());
    }

    CacheDeRelatorioDiarioCaffeine(Duration validade, Ticker relogio) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(validade)
                .maximumSize(MAXIMO_DE_ENTRADAS)
                .ticker(relogio)
                .build();
    }

    @Override
    public Optional<RelatorioDiario> buscar(Chave chave) {
        return Optional.ofNullable(cache.getIfPresent(chave));
    }

    @Override
    public void guardar(Chave chave, RelatorioDiario relatorio) {
        cache.put(chave, relatorio);
    }

    @Override
    public void descartarDoPaciente(Long pacienteId) {
        descartar(chave -> chave.pacienteId().equals(pacienteId));
    }

    @Override
    public void descartarDoCuidador(Long cuidadorId) {
        descartar(chave -> chave.cuidadorId().equals(cuidadorId));
    }

    private void descartar(Predicate<Chave> afetada) {
        cache.asMap().keySet().removeIf(afetada);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cache.asMap().keySet().removeIf(afetada);
                }
            });
        }
    }

    long tamanho() {
        cache.cleanUp();
        return cache.estimatedSize();
    }
}
