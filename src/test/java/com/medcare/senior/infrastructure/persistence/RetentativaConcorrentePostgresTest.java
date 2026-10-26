package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.application.usecase.ReenviarAvisosPendentesUseCase;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoParaRetentativa;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.infrastructure.notificacao.PushNotificadorDeDoseNaoTomadaAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.CuidadorRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.CuidadorMapper;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retentativa de avisos (RN-010) em transações reais (commit) contra o PostgreSQL, com o notificador de push real e
 * um provedor de push FALSO (nunca o FCM). A busca de eventos é restrita aos criados pelo teste, para não tocar em
 * dados de desenvolvimento do banco local. Roda sem a transação de teste e apaga o que criou.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({EventoNotificacaoRepositoryAdapter.class, DoseRepositoryAdapter.class, CuidadorRepositoryAdapter.class,
        DoseMapper.class, CuidadorMapper.class, EnvioDeAvisoService.class, ReenviarAvisosPendentesUseCase.class,
        RetentativaConcorrentePostgresTest.Configuracao.class})
class RetentativaConcorrentePostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Horários em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime T0 = LocalDateTime.of(2000, 1, 10, 9, 0);
    private static final String TOKEN = "token-aparelho-retentativa";

    /** Relógio que o teste avança. */
    static class RelogioAjustavel extends Clock {
        private volatile Instant instante = T0.atZone(FUSO).toInstant();

        void em(LocalDateTime horario) {
            instante = horario.atZone(FUSO).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return FUSO;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }

    /** Provedor de push falso: conta os envios e devolve o resultado configurado. */
    static class PushFalso implements NotificacaoPushPort {
        final AtomicInteger envios = new AtomicInteger();
        volatile ResultadoEnvioPush resposta = ResultadoEnvioPush.ENVIADO;

        @Override
        public ResultadoEnvioPush enviar(String tokenDestino, String titulo, String corpo, Map<String, String> dados) {
            envios.incrementAndGet();
            return resposta;
        }
    }

    /** IDs dos eventos criados pelo teste: a busca só devolve estes. */
    static final Set<Long> EVENTOS_DO_TESTE = ConcurrentHashMap.newKeySet();

    @TestConfiguration
    static class Configuracao {
        @Bean
        RelogioAjustavel clock() {
            return new RelogioAjustavel();
        }

        @Bean
        ReenviarAvisosPendentesUseCase.Parametros parametros() {
            return new ReenviarAvisosPendentesUseCase.Parametros(5, Duration.ofMinutes(5), 200);
        }

        @Bean
        PushFalso pushFalso() {
            return new PushFalso();
        }

        // Montado aqui (e não por @Import) porque o componente só existe com app.fcm.enabled=true
        @Bean
        PushNotificadorDeDoseNaoTomadaAdapter notificador(CuidadorRepositoryPort cuidadores, PushFalso push, Clock clock) {
            return new PushNotificadorDeDoseNaoTomadaAdapter(cuidadores, push, clock);
        }

        @Bean
        @Primary
        EventoNotificacaoRepositoryPort somenteEventosDoTeste(EventoNotificacaoRepositoryAdapter real) {
            return new EventoNotificacaoRepositoryPort() {
                @Override
                public Optional<Long> criarSeAusente(Long doseId) {
                    return real.criarSeAusente(doseId);
                }

                @Override
                public boolean reivindicar(Long eventoId, int tentativasEsperadas, LocalDateTime agora) {
                    return real.reivindicar(eventoId, tentativasEsperadas, agora);
                }

                @Override
                public List<EventoParaRetentativa> findParaRetentativa(LocalDateTime agora, int maxTentativas,
                                                                       Duration intervaloBase, int limite) {
                    return real.findParaRetentativa(agora, maxTentativas, intervaloBase, 10_000).stream()
                            .filter(e -> EVENTOS_DO_TESTE.contains(e.eventoId()))
                            .limit(limite)
                            .toList();
                }

                @Override
                public void marcarEnviado(Long eventoId) {
                    real.marcarEnviado(eventoId);
                }

                @Override
                public void marcarFalha(Long eventoId, MotivoFalhaNotificacao motivo) {
                    real.marcarFalha(eventoId, motivo);
                }

                @Override
                public void deleteByCuidadorId(Long cuidadorId) {
                    real.deleteByCuidadorId(cuidadorId);
                }
            };
        }
    }

    @Autowired
    private ReenviarAvisosPendentesUseCase useCase;

    @Autowired
    private RelogioAjustavel relogio;

    @Autowired
    private PushFalso push;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long pacienteId;
    private Long medicamentoId;
    private Long tratamentoId;
    private Long doseId;
    private Long eventoId;

    @BeforeEach
    void preparar() {
        push.envios.set(0);
        push.resposta = ResultadoEnvioPush.ENVIADO;
        relogio.em(T0);
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento, fcm_token) VALUES ('Cuidador retentativa', ?, 'hash', ?, true, ?, ?) RETURNING id",
                Long.class, "retentativa-" + UUID.randomUUID() + "@teste.com", T0, T0, TOKEN + UUID.randomUUID());
        pacienteId = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES ('Maria', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1945, 3, 12), cuidadorId);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, pacienteId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, T0.toLocalDate());
        doseId = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) "
                + "VALUES (?, ?, 'PENDENTE') RETURNING id", Long.class, tratamentoId, T0.minusMinutes(30));
        // Primeira tentativa (da varredura) feita em T0 e com falha temporária
        eventoId = jdbc.queryForObject("INSERT INTO evento_notificacao (status, tentativas, ultima_tentativa, dose_id) "
                + "VALUES ('PENDENTE', 1, ?, ?) RETURNING id", Long.class, T0, doseId);
        EVENTOS_DO_TESTE.add(eventoId);
    }

    @AfterEach
    void limpar() {
        EVENTOS_DO_TESTE.remove(eventoId);
        jdbc.update("DELETE FROM evento_notificacao WHERE id = ?", eventoId);
        jdbc.update("DELETE FROM dose WHERE id = ?", doseId);
        jdbc.update("DELETE FROM tratamento WHERE id = ?", tratamentoId);
        jdbc.update("DELETE FROM medicamento WHERE id = ?", medicamentoId);
        jdbc.update("DELETE FROM paciente WHERE id = ?", pacienteId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);
    }

    private String coluna(String nome) {
        return jdbc.queryForObject("SELECT " + nome + "::text FROM evento_notificacao WHERE id = ?", String.class, eventoId);
    }

    @Test
    void quatroRetentativasSimultaneasEnviamUmaVezSo() throws Exception {
        relogio.em(T0.plusMinutes(5));
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<ReenviarAvisosPendentesUseCase.Resultado>> futuros = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return useCase.executar();
                }));
            }
            largada.countDown();
            int tentativas = 0;
            for (Future<ReenviarAvisosPendentesUseCase.Resultado> futuro : futuros) {
                tentativas += futuro.get(30, TimeUnit.SECONDS).tentativas();
            }

            assertThat(tentativas).isEqualTo(1);
            assertThat(push.envios.get()).isEqualTo(1);
            assertThat(coluna("status")).isEqualTo("ENVIADO");
            assertThat(coluna("tentativas")).isEqualTo("2");
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cincoTentativasComIntervaloCrescenteEDepoisEsgota() {
        push.resposta = ResultadoEnvioPush.FALHA_TEMPORARIA;
        // Depois da 1ª tentativa (T0): +5 -> 2ª, +10 -> 3ª, +20 -> 4ª, +40 -> 5ª
        LocalDateTime ultima = T0;
        int[] esperas = {5, 10, 20, 40};
        for (int i = 0; i < esperas.length; i++) {
            LocalDateTime proxima = ultima.plusMinutes(esperas[i]);
            relogio.em(proxima.minusSeconds(1));
            assertThat(useCase.executar().tentativas()).as("1 s antes da tentativa %d", i + 2).isZero();
            relogio.em(proxima);
            assertThat(useCase.executar().tentativas()).as("tentativa %d", i + 2).isEqualTo(1);
            assertThat(coluna("tentativas")).isEqualTo(String.valueOf(i + 2));
            ultima = proxima;
        }

        assertThat(push.envios.get()).isEqualTo(4);
        assertThat(coluna("status")).isEqualTo("FALHA");
        assertThat(coluna("motivo_falha")).isEqualTo("TENTATIVAS_ESGOTADAS");
        relogio.em(ultima.plusHours(5));
        assertThat(useCase.executar()).isEqualTo(new ReenviarAvisosPendentesUseCase.Resultado(0, 0, 0, 0, 0));
    }

    @Test
    void tokenInvalidoEncerraOAvisoELimpaOTokenDoCuidador() {
        push.resposta = ResultadoEnvioPush.TOKEN_INVALIDO;
        relogio.em(T0.plusMinutes(5));

        useCase.executar();

        assertThat(coluna("status")).isEqualTo("FALHA");
        assertThat(coluna("motivo_falha")).isEqualTo("TOKEN_INVALIDO");
        assertThat(jdbc.queryForObject("SELECT fcm_token FROM cuidador WHERE id = ?", String.class, cuidadorId)).isNull();
        // Não insiste: a próxima rodada não tenta de novo
        relogio.em(T0.plusHours(2));
        useCase.executar();
        assertThat(push.envios.get()).isEqualTo(1);
    }

    @Test
    void cuidadorSemTokenEncerraOAvisoSemEnviar() {
        jdbc.update("UPDATE cuidador SET fcm_token = NULL WHERE id = ?", cuidadorId);
        relogio.em(T0.plusMinutes(5));

        useCase.executar();

        assertThat(push.envios.get()).isZero();
        assertThat(coluna("status")).isEqualTo("FALHA");
        assertThat(coluna("motivo_falha")).isEqualTo("SEM_TOKEN");
    }

    @Test
    void doseConfirmadaAntesDoReenvioCancelaOAviso() {
        jdbc.update("UPDATE dose SET status = 'CONFIRMADA_COM_ATRASO', hora_confirmacao = ?, confirmado_por = 'PACIENTE' WHERE id = ?",
                T0.plusMinutes(2), doseId);
        relogio.em(T0.plusMinutes(5));

        useCase.executar();

        assertThat(push.envios.get()).isZero();
        assertThat(coluna("status")).isEqualTo("FALHA");
        assertThat(coluna("motivo_falha")).isEqualTo("DOSE_JA_REGISTRADA");
    }
}
