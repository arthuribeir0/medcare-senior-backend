package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.usecase.ConfirmarDoseUseCase;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.IdosoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.mapper.IdosoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Duas confirmações da MESMA dose ao mesmo tempo (do idoso, ou do idoso e do cuidador), em transações reais (commit)
 * contra o PostgreSQL: o UPDATE condicionado a status = 'PENDENTE' deixa exatamente uma vencer. Roda sem a transação de teste e apaga o que criou.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DoseRepositoryAdapter.class, IdosoRepositoryAdapter.class, DoseMapper.class, IdosoMapper.class,
        ConfirmarDoseUseCase.class, ConfirmacaoConcorrentePostgresTest.Configuracao.class})
class ConfirmacaoConcorrentePostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime HORARIO = LocalDateTime.of(2000, 1, 10, 8, 0);
    private static final LocalDateTime AGORA = HORARIO.plusMinutes(5);

    @TestConfiguration
    static class Configuracao {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        }

        @Bean
        PoliticaDeJanela politicaDeJanela() {
            return new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(30));
        }
    }

    @Autowired
    private ConfirmarDoseUseCase confirmarDoseUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long idosoId;
    private Long medicamentoId;
    private Long tratamentoId;
    private Long doseId;

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM dose WHERE id = ?", doseId);
        jdbc.update("DELETE FROM tratamento WHERE id = ?", tratamentoId);
        jdbc.update("DELETE FROM medicamento WHERE id = ?", medicamentoId);
        jdbc.update("DELETE FROM idoso WHERE id = ?", idosoId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);
    }

    private void criarDosePendente() {
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador concorrência', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "concorrencia-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        idosoId = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Idoso " + UUID.randomUUID(), LocalDate.of(1945, 3, 12), cuidadorId);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, idosoId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, HORARIO.toLocalDate());
        doseId = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) "
                + "VALUES (?, ?, 'PENDENTE') RETURNING id", Long.class, tratamentoId, HORARIO);
    }

    @Test
    void duasConfirmacoesConcorrentesSoUmaVence() throws Exception {
        criarDosePendente();
        int tentativas = 2;
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tentativas);
        try {
            List<Future<Boolean>> resultados = new ArrayList<>();
            for (int i = 0; i < tentativas; i++) {
                Callable<Boolean> confirmar = () -> {
                    largada.await();
                    try {
                        confirmarDoseUseCase.executar(doseId, idosoId);
                        return true;
                    } catch (DoseNaoConfirmavelException e) {
                        return false;
                    }
                };
                resultados.add(executor.submit(confirmar));
            }
            largada.countDown();

            int sucessos = 0;
            for (Future<Boolean> resultado : resultados) {
                if (resultado.get(30, TimeUnit.SECONDS)) {
                    sucessos++;
                }
            }

            assertThat(sucessos).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseId))
                    .isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO.name());
        } finally {
            executor.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void idosoECuidadorConfirmandoAoMesmoTempoSoUmVenceEQuemConfirmouEhOVencedor() throws Exception {
        criarDosePendente();
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> idoso = executor.submit(() -> {
                largada.await();
                try {
                    confirmarDoseUseCase.executar(doseId, idosoId);
                    return true;
                } catch (DoseNaoConfirmavelException e) {
                    return false;
                }
            });
            Future<Boolean> cuidador = executor.submit(() -> {
                largada.await();
                try {
                    confirmarDoseUseCase.executarComoCuidador(doseId, cuidadorId);
                    return true;
                } catch (DoseNaoConfirmavelException e) {
                    return false;
                }
            });
            largada.countDown();
            boolean idosoVenceu = idoso.get(30, TimeUnit.SECONDS);
            boolean cuidadorVenceu = cuidador.get(30, TimeUnit.SECONDS);

            assertThat(idosoVenceu).isNotEqualTo(cuidadorVenceu);
            assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseId))
                    .isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO.name());
            assertThat(jdbc.queryForObject("SELECT confirmado_por FROM dose WHERE id = ?", String.class, doseId))
                    .isEqualTo(idosoVenceu ? "IDOSO" : "CUIDADOR");
        } finally {
            executor.shutdownNow();
        }
    }
}
