package com.medcare.senior.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort.Chave;
import com.medcare.senior.domain.relatorio.RelatorioDiario;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cache do relatório diário com relógio controlado: acerto, falta, expiração em 30 s e descarte por idoso, por
 * cuidador e de novo depois do commit.
 */
class CacheDeRelatorioDiarioCaffeineTest {

    private static final LocalDate HOJE = LocalDate.of(2026, 10, 22);
    private static final Chave CUIDADOR_1_IDOSO_10 = new Chave(1L, 10L, HOJE);
    private static final Chave CUIDADOR_1_IDOSO_11 = new Chave(1L, 11L, HOJE);
    private static final Chave CUIDADOR_2_IDOSO_20 = new Chave(2L, 20L, HOJE);

    /** Relógio do Caffeine em nanossegundos, avançado à mão. */
    private final AtomicLong nanos = new AtomicLong();
    private final Ticker relogio = nanos::get;
    private final CacheDeRelatorioDiarioCaffeine cache = new CacheDeRelatorioDiarioCaffeine(Duration.ofSeconds(30), relogio);

    @AfterEach
    void limparSincronizacao() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static RelatorioDiario relatorio(Long idosoId) {
        return new RelatorioDiario(idosoId, "Idoso " + idosoId, HOJE, TotaisDoDia.de(List.of()), List.of());
    }

    private void avancar(Duration tempo) {
        nanos.addAndGet(tempo.toNanos());
    }

    @Test
    void faltaAntesDeGuardarEAcertoDepois() {
        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isEmpty();

        RelatorioDiario relatorio = relatorio(10L);
        cache.guardar(CUIDADOR_1_IDOSO_10, relatorio);

        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).containsSame(relatorio);
        // A chave inteira conta: outro dia ou outro cuidador é falta
        assertThat(cache.buscar(new Chave(1L, 10L, HOJE.minusDays(1)))).isEmpty();
        assertThat(cache.buscar(new Chave(2L, 10L, HOJE))).isEmpty();
    }

    @Test
    void expiraTrintaSegundosDepoisDeGuardar() {
        cache.guardar(CUIDADOR_1_IDOSO_10, relatorio(10L));

        avancar(Duration.ofSeconds(29));
        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isPresent();
        avancar(Duration.ofSeconds(1));
        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isEmpty();
    }

    @Test
    void descartarDoIdosoTiraSoOsRelatoriosDele() {
        cache.guardar(CUIDADOR_1_IDOSO_10, relatorio(10L));
        cache.guardar(new Chave(1L, 10L, HOJE.minusDays(1)), relatorio(10L));
        cache.guardar(CUIDADOR_1_IDOSO_11, relatorio(11L));

        cache.descartarDoIdoso(10L);

        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isEmpty();
        assertThat(cache.buscar(new Chave(1L, 10L, HOJE.minusDays(1)))).isEmpty();
        assertThat(cache.buscar(CUIDADOR_1_IDOSO_11)).isPresent();
        assertThat(cache.tamanho()).isEqualTo(1);
    }

    @Test
    void descartarDoCuidadorTiraTodosOsIdososDeleESoDele() {
        cache.guardar(CUIDADOR_1_IDOSO_10, relatorio(10L));
        cache.guardar(CUIDADOR_1_IDOSO_11, relatorio(11L));
        cache.guardar(CUIDADOR_2_IDOSO_20, relatorio(20L));

        cache.descartarDoCuidador(1L);

        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isEmpty();
        assertThat(cache.buscar(CUIDADOR_1_IDOSO_11)).isEmpty();
        assertThat(cache.buscar(CUIDADOR_2_IDOSO_20)).isPresent();
    }

    @Test
    void dentroDeUmaTransacaoDescartaDeNovoDepoisDoCommit() {
        TransactionSynchronizationManager.initSynchronization();
        cache.guardar(CUIDADOR_1_IDOSO_10, relatorio(10L));

        cache.descartarDoCuidador(1L);
        // Uma leitura concorrente, antes do commit, monta e guarda o relatório com os dados antigos
        cache.guardar(CUIDADOR_1_IDOSO_10, relatorio(10L));
        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isPresent();

        List<TransactionSynchronization> sincronizacoes = TransactionSynchronizationManager.getSynchronizations();
        assertThat(sincronizacoes).hasSize(1);
        sincronizacoes.forEach(TransactionSynchronization::afterCommit);

        assertThat(cache.buscar(CUIDADOR_1_IDOSO_10)).isEmpty();
    }

    @Test
    void construtorDoSpringUsaOsSegundosConfigurados() {
        CacheDeRelatorioDiarioCaffeine doSpring = new CacheDeRelatorioDiarioCaffeine(30);
        doSpring.guardar(CUIDADOR_1_IDOSO_10, relatorio(10L));

        assertThat(doSpring.buscar(CUIDADOR_1_IDOSO_10)).isPresent();
    }
}
