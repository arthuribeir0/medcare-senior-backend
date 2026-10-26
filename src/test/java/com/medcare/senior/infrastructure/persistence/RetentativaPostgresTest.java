package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.notificacao.EventoParaRetentativa;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Busca de eventos para a retentativa (RN-010) contra o PostgreSQL real: o intervalo crescente é calculado no SQL a
 * partir de tentativas e ultima_tentativa. Cada teste roda na transação de teste e é desfeito no final.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({EventoNotificacaoRepositoryAdapter.class, DoseRepositoryAdapter.class, DoseMapper.class})
class RetentativaPostgresTest {

    // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime AGORA = LocalDateTime.of(2000, 1, 10, 12, 0);
    private static final Duration BASE = Duration.ofMinutes(5);
    private static final int MAX = 5;

    @Autowired
    private EventoNotificacaoRepositoryAdapter eventoAdapter;

    @Autowired
    private DoseRepositoryAdapter doseAdapter;

    @Autowired
    private JdbcTemplate jdbc;

    private Long pacienteId;
    private Long tratamentoId;
    private Long medicamentoId;
    private int dosesCriadas;

    @BeforeEach
    void criarDados() {
        // Eventos PENDENTES que já existam no banco local não entram nas contas destes testes (desfeito no rollback)
        jdbc.update("UPDATE evento_notificacao SET status = 'ENVIADO' WHERE status = 'PENDENTE'");
        Long cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, "
                + "consentimento_lgpd, data_consentimento) VALUES ('Cuidador retentativa', ?, 'hash', ?, true, ?) "
                + "RETURNING id", Long.class, "retentativa-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        pacienteId = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES ('Maria', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1945, 3, 12), cuidadorId);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, pacienteId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, AGORA.toLocalDate());
    }

    private long dose(LocalDateTime horario, StatusDose status) {
        boolean confirmada = status == StatusDose.CONFIRMADA_NO_HORARIO || status == StatusDose.CONFIRMADA_COM_ATRASO;
        return jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status, confirmado_por) "
                + "VALUES (?, ?, ?, ?) RETURNING id", Long.class, tratamentoId, horario, status.name(),
                confirmada ? "PACIENTE" : null);
    }

    /** Evento PENDENTE com {@code tentativas} feitas, a última {@code minutosAtras} minutos antes de AGORA. */
    private long evento(int tentativas, Integer minutosAtras) {
        // Um horário diferente por dose (índice único por tratamento e horário), no dia anterior
        long doseId = dose(AGORA.minusDays(1).plusMinutes(++dosesCriadas), StatusDose.PENDENTE);
        LocalDateTime ultima = minutosAtras == null ? null : AGORA.minusMinutes(minutosAtras);
        return eventoComDose(doseId, tentativas, ultima);
    }

    private long eventoComDose(long doseId, int tentativas, LocalDateTime ultimaTentativa) {
        return jdbc.queryForObject("INSERT INTO evento_notificacao (status, tentativas, ultima_tentativa, dose_id) "
                + "VALUES ('PENDENTE', ?, ?, ?) RETURNING id", Long.class, tentativas, ultimaTentativa, doseId);
    }

    private List<Long> elegiveis() {
        return eventoAdapter.findParaRetentativa(AGORA, MAX, BASE, 100).stream()
                .map(EventoParaRetentativa::eventoId)
                .toList();
    }

    @Test
    void intervaloCresceComAsTentativasNosLimitesExatos() {
        // Espera depois da tentativa n: 5 * 2^(n-1) minutos -> 5, 10, 20, 40
        int[][] casos = {{1, 5}, {2, 10}, {3, 20}, {4, 40}};
        for (int[] caso : casos) {
            int tentativas = caso[0];
            int espera = caso[1];
            long noLimite = eventoComDose(dose(AGORA.minusHours(3).plusMinutes(tentativas), StatusDose.PENDENTE),
                    tentativas, AGORA.minusMinutes(espera));
            long umSegundoAntes = eventoComDose(dose(AGORA.minusHours(4).plusMinutes(tentativas), StatusDose.PENDENTE),
                    tentativas, AGORA.minusMinutes(espera).plusSeconds(1));

            assertThat(elegiveis()).as("tentativas = %d", tentativas).contains(noLimite).doesNotContain(umSegundoAntes);
        }
    }

    @Test
    void eventoNuncaTentadoEhElegivelNaHoraEEsgotadoTambemEntra() {
        long nuncaTentado = evento(0, null);
        long esgotado = evento(MAX, 1);
        long recemTentado = evento(1, 1);

        assertThat(elegiveis()).contains(nuncaTentado, esgotado).doesNotContain(recemTentado);
    }

    @Test
    void eventosEnviadosOuComFalhaNaoEntram() {
        long enviado = evento(1, 60);
        long falha = evento(1, 60);
        jdbc.update("UPDATE evento_notificacao SET status = 'ENVIADO' WHERE id = ?", enviado);
        jdbc.update("UPDATE evento_notificacao SET status = 'FALHA', motivo_falha = 'SEM_TOKEN' WHERE id = ?", falha);

        assertThat(elegiveis()).doesNotContain(enviado, falha);
    }

    @Test
    void trazOStatusDaDoseEQuemAvisarEMarcaDoseSemDono() {
        long doseNoHorario = dose(AGORA.minusHours(1), StatusDose.CONFIRMADA_NO_HORARIO);
        long eventoDaNoHorario = eventoComDose(doseNoHorario, 1, AGORA.minusHours(1));
        long dosePendente = dose(AGORA.minusMinutes(70), StatusDose.PENDENTE);
        long eventoDaPendente = eventoComDose(dosePendente, 2, AGORA.minusHours(1));

        List<EventoParaRetentativa> lista = eventoAdapter.findParaRetentativa(AGORA, MAX, BASE, 100);

        EventoParaRetentativa noHorario = lista.stream().filter(e -> e.eventoId() == eventoDaNoHorario).findFirst().orElseThrow();
        assertThat(noHorario.statusDose()).isEqualTo(StatusDose.CONFIRMADA_NO_HORARIO);
        EventoParaRetentativa pendente = lista.stream().filter(e -> e.eventoId() == eventoDaPendente).findFirst().orElseThrow();
        assertThat(pendente.tentativas()).isEqualTo(2);
        assertThat(pendente.doseId()).isEqualTo(dosePendente);
        assertThat(pendente.statusDose()).isEqualTo(StatusDose.PENDENTE);
        assertThat(pendente.semDono()).isFalse();
        assertThat(pendente.pacienteId()).isEqualTo(pacienteId);
        assertThat(pendente.nomePaciente()).isEqualTo("Maria");
        assertThat(pendente.cuidadorId()).isNotNull();
        assertThat(pendente.horarioProgramado()).isEqualTo(AGORA.minusMinutes(70));

        // Medicamento removido logicamente depois do aviso: a dose fica sem dono para o aviso
        jdbc.update("UPDATE medicamento SET removido_em = ? WHERE id = ?", AGORA, medicamentoId);
        EventoParaRetentativa semDono = eventoAdapter.findParaRetentativa(AGORA, MAX, BASE, 100).stream()
                .filter(e -> e.eventoId() == eventoDaPendente).findFirst().orElseThrow();
        assertThat(semDono.semDono()).isTrue();
    }

    @Test
    void doseAnonimizadaFicaSemDono() {
        long anonimizada = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) "
                + "VALUES (NULL, ?, 'PENDENTE') RETURNING id", Long.class, AGORA.minusHours(1));
        long eventoId = eventoComDose(anonimizada, 1, AGORA.minusHours(1));

        EventoParaRetentativa evento = eventoAdapter.findParaRetentativa(AGORA, MAX, BASE, 100).stream()
                .filter(e -> e.eventoId() == eventoId).findFirst().orElseThrow();
        assertThat(evento.semDono()).isTrue();
        assertThat(evento.cuidadorId()).isNull();
    }

    @Test
    void respeitaOLimiteComecandoPelosNuncaTentadosEDepoisPelosMaisAntigos() {
        long maisAntigo = evento(1, 120);
        long nuncaTentado = evento(0, null);
        long maisNovo = evento(1, 30);

        assertThat(eventoAdapter.findParaRetentativa(AGORA, MAX, BASE, 2)).extracting(EventoParaRetentativa::eventoId)
                .containsExactly(nuncaTentado, maisAntigo);
        assertThat(elegiveis()).containsExactly(nuncaTentado, maisAntigo, maisNovo);
    }

    @Test
    void releituraDoStatusDaDose() {
        long doseId = dose(AGORA.minusHours(1), StatusDose.CONFIRMADA_COM_ATRASO);

        assertThat(doseAdapter.findStatusById(doseId)).contains(StatusDose.CONFIRMADA_COM_ATRASO);
        assertThat(doseAdapter.findStatusById(-1L)).isEmpty();
    }
}
