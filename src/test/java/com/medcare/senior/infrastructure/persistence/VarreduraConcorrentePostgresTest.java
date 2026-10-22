package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.application.usecase.ConfirmarDoseUseCase;
import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.notificacao.DoseNaoTomadaNotificacao;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Varredura de doses em transações reais (commit) contra o PostgreSQL, com execuções simultâneas: o evento único por
 * dose e a reivindicação atômica garantem um só aviso; o UPDATE condicionado a PENDENTE deixa só um vencer entre o
 * encerramento e a confirmação. O push é um fake (nunca o FCM). Roda sem a transação de teste e apaga o que criou.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DoseRepositoryAdapter.class, IdosoRepositoryAdapter.class, EventoNotificacaoRepositoryAdapter.class,
        DoseMapper.class, IdosoMapper.class, ConfirmarDoseUseCase.class, EnvioDeAvisoService.class,
        VerificarDosesVencidasUseCase.class, VarreduraConcorrentePostgresTest.Configuracao.class})
class VarreduraConcorrentePostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime AGORA = LocalDateTime.of(2000, 1, 10, 11, 0);

    /**
     * Notificador falso: registra quem foi avisado e responde ENVIADO.
     */
    static class NotificadorFalso implements NotificadorDeDoseNaoTomadaPort {
        final List<DoseNaoTomadaNotificacao> avisos = new CopyOnWriteArrayList<>();

        @Override
        public ResultadoEnvioPush notificar(DoseNaoTomadaNotificacao notificacao) {
            avisos.add(notificacao);
            return ResultadoEnvioPush.ENVIADO;
        }
    }

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
        NotificadorFalso notificadorFalso() {
            return new NotificadorFalso();
        }
    }

    @Autowired
    private VerificarDosesVencidasUseCase verificarDosesVencidasUseCase;

    @Autowired
    private ConfirmarDoseUseCase confirmarDoseUseCase;

    @Autowired
    private NotificadorFalso notificador;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long idosoId;
    private Long medicamentoId;
    private Long tratamentoId;
    private Long doseId;

    @AfterEach
    void limpar() {
        notificador.avisos.clear();
        jdbc.update("DELETE FROM evento_notificacao WHERE dose_id = ?", doseId);
        jdbc.update("DELETE FROM dose WHERE id = ?", doseId);
        jdbc.update("DELETE FROM tratamento WHERE id = ?", tratamentoId);
        jdbc.update("DELETE FROM medicamento WHERE id = ?", medicamentoId);
        jdbc.update("DELETE FROM idoso WHERE id = ?", idosoId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);
    }

    private void criarDosePendente(LocalDateTime horario) {
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador varredura', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "varredura-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        idosoId = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Idoso " + UUID.randomUUID(), LocalDate.of(1945, 3, 12), cuidadorId);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, idosoId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, horario.toLocalDate());
        doseId = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) "
                + "VALUES (?, ?, 'PENDENTE') RETURNING id", Long.class, tratamentoId, horario);
    }

    private <T> List<T> emParalelo(int vezes, Callable<T> tarefa) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(vezes);
        try {
            List<Future<T>> futuros = new ArrayList<>();
            for (int i = 0; i < vezes; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<T> resultados = new ArrayList<>();
            for (Future<T> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void duasVarredurasSimultaneasCriamUmEventoEAvisamUmaVezSoEADoseContinuaPendente() throws Exception {
        // Passou da tolerância (30 min) há 10 min e falta muito para o encerramento
        criarDosePendente(AGORA.minusMinutes(40));

        List<VerificarDosesVencidasUseCase.Resultado> resultados = emParalelo(4, verificarDosesVencidasUseCase::executar);

        assertThat(resultados.stream().mapToInt(VerificarDosesVencidasUseCase.Resultado::avisos).sum()).isEqualTo(1);
        assertThat(notificador.avisos).hasSize(1);
        assertThat(notificador.avisos.get(0).doseId()).isEqualTo(doseId);
        assertThat(notificador.avisos.get(0).idosoId()).isEqualTo(idosoId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM evento_notificacao WHERE dose_id = ?", Long.class, doseId))
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM evento_notificacao WHERE dose_id = ?", String.class, doseId))
                .isEqualTo("ENVIADO");
        assertThat(jdbc.queryForObject("SELECT tentativas FROM evento_notificacao WHERE dose_id = ?", Integer.class, doseId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseId))
                .isEqualTo(StatusDose.PENDENTE.name());

        // Uma terceira execução, depois, também não avisa de novo
        assertThat(verificarDosesVencidasUseCase.executar().avisos()).isZero();
        assertThat(notificador.avisos).hasSize(1);
    }

    @RepeatedTest(10)
    void encerramentoEConfirmacaoNoMesmoInstanteSoUmVence() throws Exception {
        // Exatamente horário + tolerância padrão (30) + margem (30): o job encerra (<=) e a confirmação ainda é aceita
        // (limite inclusivo). Repetido para variar quem chega primeiro ao banco.
        criarDosePendente(AGORA.minusMinutes(60));

        corridaEntreEncerramentoEConfirmacao();
    }

    @RepeatedTest(5)
    void encerramentoEConfirmacaoComAToleranciaDoCuidadorSoUmVence() throws Exception {
        // Cuidador com tolerância máxima (120): a dose encerra em horário + 150 min, não em horário + 60
        criarDosePendente(AGORA.minusMinutes(150));
        jdbc.update("INSERT INTO configuracao_alerta (cuidador_id, tolerancia_minutos) VALUES (?, 120)", cuidadorId);

        corridaEntreEncerramentoEConfirmacao();
    }

    private void corridaEntreEncerramentoEConfirmacao() throws Exception {
        List<Boolean> venceu = emParalelo(2, new Callable<>() {
            private final java.util.concurrent.atomic.AtomicInteger vez = new java.util.concurrent.atomic.AtomicInteger();

            @Override
            public Boolean call() {
                if (vez.getAndIncrement() == 0) {
                    return verificarDosesVencidasUseCase.executar().encerradas() == 1;
                }
                try {
                    confirmarDoseUseCase.executar(doseId, idosoId);
                    return true;
                } catch (DoseNaoConfirmavelException e) {
                    return false;
                }
            }
        });

        assertThat(venceu.stream().filter(Boolean::booleanValue).count()).isEqualTo(1);
        String status = jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseId);
        assertThat(status).isIn(StatusDose.NAO_TOMADA.name(), StatusDose.CONFIRMADA_COM_ATRASO.name());
        // Quem perdeu não mudou nada: confirmada tem hora; encerrada não
        Object hora = jdbc.queryForObject("SELECT hora_confirmacao FROM dose WHERE id = ?", Object.class, doseId);
        if (StatusDose.NAO_TOMADA.name().equals(status)) {
            assertThat(hora).isNull();
        } else {
            assertThat(hora).isNotNull();
        }
        // Encerramento não avisa ninguém
        assertThat(notificador.avisos).isEmpty();
    }
}
