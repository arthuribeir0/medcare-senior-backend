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
        assertThat(resultado.targetSchemaVersion).isEqualTo("16");
        assertThat(resultado.migrationsExecuted).isEqualTo(16);

        List<String> colunas = jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name = 'mensagem' ORDER BY ordinal_position", String.class, schema);
        assertThat(colunas).containsExactly("id", "paciente_id", "remetente", "conteudo", "criada_em", "lida_em");
        List<String> constraints = jdbc.queryForList("SELECT conname FROM pg_constraint c JOIN pg_namespace n "
                + "ON n.oid = c.connamespace WHERE n.nspname = ? AND conrelid = (?)::regclass ORDER BY conname",
                String.class, schema, schema + ".mensagem");
        assertThat(constraints).contains("ck_mensagem_conteudo", "ck_mensagem_remetente", "mensagem_paciente_id_fkey");
        List<String> indices = jdbc.queryForList("SELECT indexname FROM pg_indexes WHERE schemaname = ? "
                + "AND tablename = 'mensagem'", String.class, schema);
        assertThat(indices).contains("idx_mensagem_paciente_criada_em", "idx_mensagem_nao_lidas");
    }

    @Test
    void aV15ConverteAsDosesTomadaParaConfirmadaNoHorarioPreencheQuemConfirmouEOCheckSoAceitaONomeNovo() {
        flyway("14").migrate();
        // Dose anonimizada (sem tratamento): a mais simples de criar com o schema da V14
        jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, hora_confirmacao, status) "
                + "VALUES ('2000-01-10 08:00', '2000-01-10 08:05', 'TOMADA')");
        jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, status) VALUES ('2000-01-10 09:00', 'NAO_TOMADA')");

        flyway(null).migrate();

        assertThat(jdbc.queryForList("SELECT status FROM " + schema + ".dose ORDER BY horario_programado", String.class))
                .containsExactly("CONFIRMADA_NO_HORARIO", "NAO_TOMADA");
        // Até a V15 só o paciente confirmava: as confirmadas recebem IDOSO, que a V16 troca por PACIENTE; a não tomada
        // fica sem quem confirmou
        assertThat(jdbc.queryForList("SELECT confirmado_por FROM " + schema + ".dose ORDER BY horario_programado",
                String.class)).containsExactly("PACIENTE", null);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, status) "
                + "VALUES ('2000-01-10 10:00', 'TOMADA')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aV16RenomeiaIdosoParaPacienteNasTabelasENosValoresGravados() {
        flyway("15").migrate();
        Long cuidador = jdbc.queryForObject("INSERT INTO " + schema + ".cuidador (nome, email, senha_hash, data_cadastro) "
                + "VALUES ('Ana', 'ana@teste.com', 'hash', '2000-01-10 08:00') RETURNING id", Long.class);
        Long idoso = jdbc.queryForObject("INSERT INTO " + schema + ".idoso (nome, data_nascimento, cuidador_id) "
                + "VALUES ('José', '1945-03-12', ?) RETURNING id", Long.class, cuidador);
        jdbc.update("INSERT INTO " + schema + ".mensagem (idoso_id, remetente, conteudo, criada_em) "
                + "VALUES (?, 'IDOSO', 'Bom dia', '2000-01-10 08:00')", idoso);
        jdbc.update("INSERT INTO " + schema + ".dose (horario_programado, hora_confirmacao, status, confirmado_por) "
                + "VALUES ('2000-01-10 08:00', '2000-01-10 08:05', 'CONFIRMADA_NO_HORARIO', 'IDOSO')");

        flyway(null).migrate();

        assertThat(jdbc.queryForObject("SELECT nome FROM " + schema + ".paciente WHERE id = ?", String.class, idoso))
                .isEqualTo("José");
        assertThat(jdbc.queryForObject("SELECT remetente FROM " + schema + ".mensagem WHERE paciente_id = ?", String.class,
                idoso)).isEqualTo("PACIENTE");
        assertThat(jdbc.queryForObject("SELECT confirmado_por FROM " + schema + ".dose", String.class)).isEqualTo("PACIENTE");
        // O nome antigo não é mais aceito nos CHECKs, e o id segue a sequência renomeada
        assertThatThrownBy(() -> jdbc.update("INSERT INTO " + schema + ".mensagem (paciente_id, remetente, conteudo, "
                + "criada_em) VALUES (?, 'IDOSO', 'Oi', '2000-01-10 09:00')", idoso))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("INSERT INTO " + schema + ".paciente (nome, data_nascimento, cuidador_id) "
                + "VALUES ('Helena', '1950-07-01', ?) RETURNING id", Long.class, cuidador)).isGreaterThan(idoso);
    }

    @Test
    void soDoseConfirmadaTemQuemConfirmouEOValorEhPacienteOuCuidador() {
        flyway(null).migrate();
        String insert = "INSERT INTO " + schema + ".dose (horario_programado, status, confirmado_por) VALUES (?, ?, ?)";

        // Pendente ou não tomada com quem confirmou, confirmada sem quem confirmou, ou valor desconhecido: o CHECK recusa
        List<String[]> invalidos = List.of(new String[]{"PENDENTE", "PACIENTE"}, new String[]{"NAO_TOMADA", "CUIDADOR"},
                new String[]{"CONFIRMADA_NO_HORARIO", null}, new String[]{"CONFIRMADA_COM_ATRASO", null},
                new String[]{"CONFIRMADA_NO_HORARIO", "VIZINHO"});
        for (String[] invalido : invalidos) {
            assertThatThrownBy(() -> jdbc.update(insert, java.sql.Timestamp.valueOf("2000-01-10 08:00:00"), invalido[0],
                    invalido[1]))
                    .as("%s / %s", invalido[0], invalido[1])
                    .isInstanceOf(DataIntegrityViolationException.class)
                    .hasMessageContaining("ck_dose_confirmado_por");
        }
        jdbc.update(insert, java.sql.Timestamp.valueOf("2000-01-10 08:00:00"), "CONFIRMADA_NO_HORARIO", "PACIENTE");
        jdbc.update(insert, java.sql.Timestamp.valueOf("2000-01-10 09:00:00"), "CONFIRMADA_COM_ATRASO", "CUIDADOR");
        jdbc.update(insert, java.sql.Timestamp.valueOf("2000-01-10 10:00:00"), "PENDENTE", null);
        jdbc.update(insert, java.sql.Timestamp.valueOf("2000-01-10 11:00:00"), "NAO_TOMADA", null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".dose", Long.class)).isEqualTo(4L);
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
