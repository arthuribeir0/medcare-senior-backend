package com.medcare.senior.presentation;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Monta, por SQL, um paciente com tudo o que a desvinculação apaga ou anonimiza: remédio, tratamento, dose pendente,
 * dose registrada com aviso, código de vínculo, mensagem e pedido de desconexão. Lê o estado real do banco.
 */
final class DadosDeDesvinculacao {

    record Paciente(Long id, Long medicamentoId, Long tratamentoId, Long dosePendenteId, Long doseRegistradaId) {
    }

    private final JdbcTemplate jdbc;
    private final LocalDateTime agora;

    DadosDeDesvinculacao(JdbcTemplate jdbc, LocalDateTime agora) {
        this.jdbc = jdbc;
        this.agora = agora;
    }

    Long cuidador(String nome) {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                        + "data_consentimento) VALUES (?, ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                nome, "desvinculo-" + UUID.randomUUID() + "@teste.com", agora, agora);
    }

    Paciente pacienteCompleto(Long cuidadorId, String nome) {
        Long pacienteId = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) "
                + "VALUES (?, ?, ?) RETURNING id", Long.class, nome, LocalDate.of(1945, 3, 12), cuidadorId);
        Long medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, pacienteId);
        Long tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, agora.toLocalDate().minusDays(10));
        Long pendente = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) "
                + "VALUES (?, ?, 'PENDENTE') RETURNING id", Long.class, tratamentoId, agora.plusDays(1));
        Long registrada = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, hora_confirmacao, "
                        + "status, confirmado_por) VALUES (?, ?, ?, 'CONFIRMADA_NO_HORARIO', 'PACIENTE') RETURNING id",
                Long.class, tratamentoId, agora.minusDays(1), agora.minusDays(1).plusMinutes(5));
        jdbc.update("INSERT INTO evento_notificacao (status, tentativas, dose_id) VALUES ('ENVIADO', 1, ?)", registrada);
        jdbc.update("INSERT INTO codigo_vinculo (codigo_unico, cuidador_id, paciente_id, expira_em, usado) "
                + "VALUES ('123456', ?, ?, ?, false)", cuidadorId, pacienteId, agora.plusDays(1));
        jdbc.update("INSERT INTO mensagem (paciente_id, remetente, conteudo, criada_em) VALUES (?, 'PACIENTE', 'Oi', ?)",
                pacienteId, agora);
        jdbc.update("INSERT INTO pedido_desconexao (paciente_id, status, criado_em, expira_em) "
                + "VALUES (?, 'PENDENTE', ?, ?)", pacienteId, agora, agora.plusHours(24));
        return new Paciente(pacienteId, medicamentoId, tratamentoId, pendente, registrada);
    }

    long contar(String tabela, String coluna, Long valor) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + tabela + " WHERE " + coluna + " = ?", Long.class, valor);
    }

    void assertIntacto(Paciente paciente) {
        assertThat(contar("paciente", "id", paciente.id())).isEqualTo(1);
        assertThat(contar("medicamento", "id", paciente.medicamentoId())).isEqualTo(1);
        assertThat(contar("tratamento", "id", paciente.tratamentoId())).isEqualTo(1);
        assertThat(contar("dose", "tratamento_id", paciente.tratamentoId())).isEqualTo(2);
        assertThat(contar("evento_notificacao", "dose_id", paciente.doseRegistradaId())).isEqualTo(1);
        assertThat(contar("codigo_vinculo", "paciente_id", paciente.id())).isEqualTo(1);
        assertThat(contar("mensagem", "paciente_id", paciente.id())).isEqualTo(1);
        assertThat(contar("pedido_desconexao", "paciente_id", paciente.id())).isEqualTo(1);
    }
}
