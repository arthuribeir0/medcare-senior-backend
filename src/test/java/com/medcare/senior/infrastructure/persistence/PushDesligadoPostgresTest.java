package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.application.usecase.ReenviarAvisosPendentesUseCase;
import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.infrastructure.notificacao.LogNotificadorDeDoseNaoTomadaAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import org.junit.jupiter.api.AfterEach;
import com.medcare.senior.infrastructure.cache.CacheDeRelatorioDiarioCaffeine;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Avisos com o push desligado (app.fcm.enabled=false, o notificador só de log), em transações reais contra o
 * PostgreSQL: o evento da dose vira FALHA com motivo PUSH_DESLIGADO na primeira tentativa, um só por dose e sem
 * retentativa. Dose já confirmada continua cancelando o aviso pendente com DOSE_JA_REGISTRADA.
 */
@DataJpaTest(properties = {"spring.jpa.show-sql=false", "app.fcm.enabled=false"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({CacheDeRelatorioDiarioCaffeine.class, DoseRepositoryAdapter.class, EventoNotificacaoRepositoryAdapter.class, DoseMapper.class,
        LogNotificadorDeDoseNaoTomadaAdapter.class, EnvioDeAvisoService.class, VerificarDosesVencidasUseCase.class,
        ReenviarAvisosPendentesUseCase.class, PushDesligadoPostgresTest.Configuracao.class})
class PushDesligadoPostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime AGORA = LocalDateTime.of(2000, 2, 14, 15, 0);

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

        @Bean
        VerificarDosesVencidasUseCase.Parametros parametros() {
            return new VerificarDosesVencidasUseCase.Parametros(500);
        }

        @Bean
        ReenviarAvisosPendentesUseCase.Parametros parametrosDeRetentativa() {
            return new ReenviarAvisosPendentesUseCase.Parametros(5, Duration.ofMinutes(5), 200);
        }
    }

    @Autowired
    private VerificarDosesVencidasUseCase verificarDosesVencidasUseCase;

    @Autowired
    private ReenviarAvisosPendentesUseCase reenviarAvisosPendentesUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long pacienteId;
    private Long medicamentoId;
    private Long tratamentoId;

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM evento_notificacao WHERE dose_id IN (SELECT id FROM dose WHERE tratamento_id = ?)",
                tratamentoId);
        jdbc.update("DELETE FROM dose WHERE tratamento_id = ?", tratamentoId);
        jdbc.update("DELETE FROM tratamento WHERE id = ?", tratamentoId);
        jdbc.update("DELETE FROM medicamento WHERE id = ?", medicamentoId);
        jdbc.update("DELETE FROM paciente WHERE id = ?", pacienteId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);
    }

    private void criarTratamento() {
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador sem push', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "sem-push-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        pacienteId = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) "
                + "RETURNING id", Long.class, "Paciente " + UUID.randomUUID(), LocalDate.of(1945, 3, 12), cuidadorId);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, pacienteId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, AGORA.toLocalDate());
    }

    private Long dose(LocalDateTime horario) {
        return jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (?, ?, 'PENDENTE') "
                + "RETURNING id", Long.class, tratamentoId, horario);
    }

    private String colunaDoEvento(Long doseId, String coluna) {
        return jdbc.queryForObject("SELECT " + coluna + "::text FROM evento_notificacao WHERE dose_id = ?", String.class,
                doseId);
    }

    @Test
    void comOPushDesligadoOJobRegistraFalhaPushDesligadoUmaVezSemRetentativa() {
        criarTratamento();
        // Passou da tolerância (30 min) e ainda não encerrou (60 min)
        Long doseId = dose(AGORA.minusMinutes(40));

        VerificarDosesVencidasUseCase.Resultado resultado = verificarDosesVencidasUseCase.executar();

        assertThat(resultado.avisos()).isEqualTo(1);
        assertThat(resultado.enviados()).isZero();
        assertThat(colunaDoEvento(doseId, "status")).isEqualTo("FALHA");
        assertThat(colunaDoEvento(doseId, "motivo_falha")).isEqualTo("PUSH_DESLIGADO");
        assertThat(colunaDoEvento(doseId, "tentativas")).isEqualTo("1");
        // A dose continua PENDENTE: o paciente (ou o cuidador) ainda pode confirmar até o encerramento
        assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseId)).isEqualTo("PENDENTE");

        // Nem a varredura seguinte nem a retentativa tentam de novo
        assertThat(verificarDosesVencidasUseCase.executar().avisos()).isZero();
        assertThat(reenviarAvisosPendentesUseCase.executar().tentativas()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_notificacao WHERE dose_id = ?", Long.class, doseId))
                .isEqualTo(1L);
        assertThat(colunaDoEvento(doseId, "tentativas")).isEqualTo("1");
    }

    @Test
    void avisoPendenteDeDoseJaConfirmadaContinuaDoseJaRegistradaComOPushDesligado() {
        criarTratamento();
        Long confirmada = dose(AGORA.minusMinutes(40));
        Long pendente = dose(AGORA.minusMinutes(45));
        // Eventos que ficaram PENDENTES (ex.: criados antes de o push ser desligado), já elegíveis para a retentativa
        for (Long doseId : new Long[]{confirmada, pendente}) {
            jdbc.update("INSERT INTO evento_notificacao (status, tentativas, ultima_tentativa, dose_id) "
                    + "VALUES ('PENDENTE', 1, ?, ?)", AGORA.minusMinutes(10), doseId);
        }
        jdbc.update("UPDATE dose SET status = 'CONFIRMADA_COM_ATRASO', hora_confirmacao = ?, confirmado_por = 'CUIDADOR' "
                + "WHERE id = ?", AGORA.minusMinutes(5), confirmada);

        reenviarAvisosPendentesUseCase.executar();

        assertThat(colunaDoEvento(confirmada, "status")).isEqualTo("FALHA");
        assertThat(colunaDoEvento(confirmada, "motivo_falha")).isEqualTo("DOSE_JA_REGISTRADA");
        assertThat(colunaDoEvento(pendente, "status")).isEqualTo("FALHA");
        assertThat(colunaDoEvento(pendente, "motivo_falha")).isEqualTo("PUSH_DESLIGADO");
    }
}
