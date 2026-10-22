package com.medcare.senior.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Todas as migrations, da V1 à última, aplicadas do zero num schema vazio e descartável do PostgreSQL real (o schema
 * de sempre já recebe as migrations novas por cima do banco existente ao subir o contexto dos outros testes).
 */
@JdbcTest(properties = "spring.flyway.enabled=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MigracoesDoZeroPostgresTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    private final String schema = "migracoes_do_zero_" + UUID.randomUUID().toString().replace("-", "");

    @AfterEach
    void apagarSchema() {
        jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void aplicaTodasAsMigrationsNumSchemaVazioECriaATabelaDeMensagens() {
        MigrateResult resultado = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .createSchemas(true)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        assertThat(resultado.success).isTrue();
        assertThat(resultado.targetSchemaVersion).isEqualTo("15");
        assertThat(resultado.migrationsExecuted).isEqualTo(15);

        List<String> colunas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'mensagem' ORDER BY ordinal_position", String.class, schema);
        assertThat(colunas).containsExactly("id", "idoso_id", "remetente", "conteudo", "criada_em", "lida_em");
        List<String> constraints = jdbc.queryForList("SELECT conname FROM pg_constraint c JOIN pg_namespace n "
                + "ON n.oid = c.connamespace WHERE n.nspname = ? AND conrelid = (?)::regclass ORDER BY conname",
                String.class, schema, schema + ".mensagem");
        assertThat(constraints).contains("ck_mensagem_conteudo", "ck_mensagem_remetente", "mensagem_idoso_id_fkey");
        List<String> indices = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = ? "
                + "AND tablename = 'mensagem'", String.class, schema);
        assertThat(indices).contains("idx_mensagem_idoso_criada_em", "idx_mensagem_nao_lidas");
    }

    @Test
    void aV15ConverteAsDosesTomadaParaConfirmadaNoHorarioEOCheckSoAceitaONomeNovo() {
        flyway("14").migrate();
        // Dose anonimizada (sem tratamento): a mais simples de criar com o schema da V14
        jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, hora_confirmacao, status) "
                + "VALUES ('2000-01-10 08:00', '2000-01-10 08:05', 'TOMADA')");
        jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, status) VALUES ('2000-01-10 09:00', 'NAO_TOMADA')");

        flyway(null).migrate();

        assertThat(jdbc.queryForList("SELECT status FROM " + schema + ".dose ORDER BY horario_programado", String.class))
                .containsExactly("CONFIRMADA_NO_HORARIO", "NAO_TOMADA");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, status) "
                + "VALUES ('2000-01-10 10:00', 'TOMADA')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Flyway flyway(String alvo) {
        var configuracao = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .createSchemas(true)
                .locations("classpath:db/migration");
        if (alvo != null) {
            configuracao.target(alvo);
        }
        return configuracao.load();
    }
}
