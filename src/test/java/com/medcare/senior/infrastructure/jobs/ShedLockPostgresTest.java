package com.medcare.senior.infrastructure.jobs;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ShedLock contra o PostgreSQL real (tabela shedlock da V9, relógio do banco). O provider grava a trava na própria
 * transação (commit de verdade), então este teste roda sem a transação de teste e apaga a linha que criou no final.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ShedLockConfig.class)
class ShedLockPostgresTest {

    // Nome único por execução: não colide com os jobs reais nem com execuções anteriores do teste
    private final String nome = "teste-" + UUID.randomUUID();

    @Autowired
    private LockProvider lockProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void limpar() {
        jdbcTemplate.update("DELETE FROM shedlock WHERE name = ?", nome);
    }

    private LockConfiguration configuracao() {
        return new LockConfiguration(Instant.now(), nome, Duration.ofMinutes(5), Duration.ZERO);
    }

    @Test
    void doisLocksComOMesmoNomeNaoSaoConcedidosAoMesmoTempo() {
        Optional<SimpleLock> primeiro = lockProvider.lock(configuracao());
        Optional<SimpleLock> segundo = lockProvider.lock(configuracao());

        assertThat(primeiro).isPresent();
        assertThat(segundo).isEmpty();

        // Liberada a primeira trava, o mesmo nome volta a ser concedido
        primeiro.get().unlock();
        Optional<SimpleLock> depoisDeLiberar = lockProvider.lock(configuracao());
        assertThat(depoisDeLiberar).isPresent();
        depoisDeLiberar.get().unlock();
    }

    @Test
    void locksComNomesDiferentesNaoSeBloqueiam() {
        String outroNome = nome + "-outro";
        try {
            Optional<SimpleLock> primeiro = lockProvider.lock(configuracao());
            Optional<SimpleLock> outro = lockProvider.lock(
                    new LockConfiguration(Instant.now(), outroNome, Duration.ofMinutes(5), Duration.ZERO));

            assertThat(primeiro).isPresent();
            assertThat(outro).isPresent();
        } finally {
            jdbcTemplate.update("DELETE FROM shedlock WHERE name = ?", outroNome);
        }
    }
}
