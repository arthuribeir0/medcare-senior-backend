package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.EnvioDeAvisoService;
import com.medcare.senior.application.usecase.LembrarPacienteDaDoseUseCase;
import com.medcare.senior.application.usecase.ReenviarAvisosPendentesUseCase;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.notificacao.EventoNotificacaoRepositoryPort;
import com.medcare.senior.domain.notificacao.EventoParaRetentativa;
import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.OpcoesDeEntrega;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.notificacao.TipoEventoNotificacao;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.infrastructure.notificacao.LogNotificadorDeDoseNaoTomadaAdapter;
import com.medcare.senior.infrastructure.notificacao.PushNotificadorDeLembreteDoPacienteAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.DoseRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.EventoNotificacaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.PacienteRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.mapper.PacienteMapper;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lembrete do servidor ao paciente em transações reais (commit) contra o PostgreSQL, com o job rodando mais de uma vez
 * e ao mesmo tempo: evento único por dose e tipo, nada antes de 2 minutos nem depois do aviso ao cuidador, nada para
 * dose confirmada, token inválido apagado e retentativa. O push é falso (nunca o FCM). A retentativa só enxerga os
 * eventos deste teste (o banco local pode ter dados de desenvolvimento). Roda sem a transação de teste e apaga o que
 * criou. Horários em 2001: nenhum dado de desenvolvimento nem de outro teste interfere.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DoseRepositoryAdapter.class, PacienteRepositoryAdapter.class, EventoNotificacaoRepositoryAdapter.class,
        DoseMapper.class, PacienteMapper.class, LogNotificadorDeDoseNaoTomadaAdapter.class, EnvioDeAvisoService.class,
        LembrarPacienteDaDoseUseCase.class, ReenviarAvisosPendentesUseCase.class,
        LembreteDoPacientePostgresTest.Configuracao.class})
class LembreteDoPacientePostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2001, 3, 5, 9, 0);
    private static final String TOKEN = "token-celular-lembrete";

    /** Doses criadas pelo teste: a retentativa só vê os eventos delas. */
    static final Set<Long> DOSES_DO_TESTE = ConcurrentHashMap.newKeySet();

    /** Relógio que o teste avança (retentativa depois do intervalo). */
    static class RelogioAjustavel extends Clock {
        private volatile Instant instante = AGORA.atZone(FUSO).toInstant();

        void ajustar(LocalDateTime agora) {
            instante = agora.atZone(FUSO).toInstant();
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

    record Envio(String token, String titulo, String corpo, Map<String, String> dados, OpcoesDeEntrega opcoes) {
    }

    /** Provedor de push falso: registra os envios e devolve o resultado configurado. */
    static class PushFalso implements NotificacaoPushPort {
        final List<Envio> envios = new CopyOnWriteArrayList<>();
        volatile ResultadoEnvioPush resposta = ResultadoEnvioPush.ENVIADO;

        @Override
        public ResultadoEnvioPush enviar(String tokenDestino, String titulo, String corpo, Map<String, String> dados,
                                         OpcoesDeEntrega opcoes) {
            envios.add(new Envio(tokenDestino, titulo, corpo, dados, opcoes));
            return resposta;
        }
    }

    @TestConfiguration
    static class Configuracao {
        @Bean
        RelogioAjustavel clock() {
            return new RelogioAjustavel();
        }

        @Bean
        PoliticaDeJanela politicaDeJanela() {
            return new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(30));
        }

        @Bean
        LembrarPacienteDaDoseUseCase.Parametros parametrosDoLembrete() {
            return new LembrarPacienteDaDoseUseCase.Parametros(Duration.ofMinutes(2), 500);
        }

        @Bean
        ReenviarAvisosPendentesUseCase.Parametros parametrosDaRetentativa() {
            return new ReenviarAvisosPendentesUseCase.Parametros(5, Duration.ofMinutes(5), 200);
        }

        @Bean
        PushFalso pushFalso() {
            return new PushFalso();
        }

        // Montado aqui (e não por @Import) porque o componente só existe com app.fcm.enabled=true
        @Bean
        PushNotificadorDeLembreteDoPacienteAdapter notificadorDoPaciente(PacienteRepositoryPort pacientes, PushFalso push,
                                                                        Clock clock) {
            return new PushNotificadorDeLembreteDoPacienteAdapter(pacientes, push, clock);
        }

        @Bean
        @Primary
        EventoNotificacaoRepositoryPort somenteEventosDoTeste(EventoNotificacaoRepositoryAdapter real) {
            return new EventoNotificacaoRepositoryPort() {
                @Override
                public Optional<Long> criarSeAusente(Long doseId, TipoEventoNotificacao tipo) {
                    return real.criarSeAusente(doseId, tipo);
                }

                @Override
                public boolean reivindicar(Long eventoId, int tentativasEsperadas, LocalDateTime agora) {
                    return real.reivindicar(eventoId, tentativasEsperadas, agora);
                }

                @Override
                public List<EventoParaRetentativa> findParaRetentativa(LocalDateTime agora, int maxTentativas,
                                                                       Duration intervaloBase, int limite) {
                    return real.findParaRetentativa(agora, maxTentativas, intervaloBase, 10_000).stream()
                            .filter(evento -> DOSES_DO_TESTE.contains(evento.doseId()))
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

                @Override
                public void deleteByPacienteId(Long pacienteId) {
                    real.deleteByPacienteId(pacienteId);
                }
            };
        }
    }

    @Autowired
    private LembrarPacienteDaDoseUseCase lembrar;

    @Autowired
    private ReenviarAvisosPendentesUseCase reenviar;

    @Autowired
    private EventoNotificacaoRepositoryPort eventos;

    @Autowired
    private PushFalso push;

    @Autowired
    private RelogioAjustavel relogio;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long pacienteId;
    private Long medicamentoId;
    private Long tratamentoId;

    @BeforeEach
    void criarPacienteComAparelho() {
        relogio.ajustar(AGORA);
        push.envios.clear();
        push.resposta = ResultadoEnvioPush.ENVIADO;
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador lembrete', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "lembrete-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        pacienteId = paciente(TOKEN);
        medicamentoId = medicamento(pacienteId);
        tratamentoId = tratamento(medicamentoId);
    }

    @AfterEach
    void limpar() {
        for (Long dose : DOSES_DO_TESTE) {
            jdbc.update("DELETE FROM evento_notificacao WHERE dose_id = ?", dose);
            jdbc.update("DELETE FROM dose WHERE id = ?", dose);
        }
        DOSES_DO_TESTE.clear();
        jdbc.update("DELETE FROM tratamento WHERE medicamento_id IN (SELECT id FROM medicamento WHERE paciente_id IN "
                + "(SELECT id FROM paciente WHERE cuidador_id = ?))", cuidadorId);
        jdbc.update("DELETE FROM medicamento WHERE paciente_id IN (SELECT id FROM paciente WHERE cuidador_id = ?)", cuidadorId);
        jdbc.update("DELETE FROM configuracao_alerta WHERE cuidador_id = ?", cuidadorId);
        jdbc.update("DELETE FROM paciente WHERE cuidador_id = ?", cuidadorId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);
    }

    @Test
    void jobRodandoAoMesmoTempoEDepoisEnviaUmLembreteSoComOTextoECanalDoPaciente() throws Exception {
        Long dose = dose(tratamentoId, AGORA.minusMinutes(5), "PENDENTE");

        List<LembrarPacienteDaDoseUseCase.Resultado> resultados = emParalelo(4, lembrar::executar);
        LembrarPacienteDaDoseUseCase.Resultado depois = lembrar.executar();

        assertThat(resultados.stream().mapToInt(LembrarPacienteDaDoseUseCase.Resultado::lembretes).sum()).isEqualTo(1);
        assertThat(depois.lembretes()).isZero();
        assertThat(push.envios).hasSize(1);
        Envio envio = push.envios.get(0);
        assertThat(envio.token()).isEqualTo(TOKEN);
        assertThat(envio.titulo()).isEqualTo("Hora do remédio");
        assertThat(envio.corpo()).isEqualTo("Tomar Losartana às 08:55");
        assertThat(envio.dados()).containsEntry("tipo", "LEMBRETE_DOSE").containsEntry("doseId", String.valueOf(dose));
        assertThat(envio.opcoes().canalAndroid()).isEqualTo("lembrete-dose");
        assertThat(envio.opcoes().tag()).isEqualTo("dose-" + dose + "-1");
        assertThat(eventosDa(dose)).containsExactly("LEMBRETE_PACIENTE:ENVIADO:1");
        // O lembrete não muda a dose
        assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, dose)).isEqualTo("PENDENTE");
    }

    @Test
    void naoEnviaAntesDeDoisMinutosNemParaDoseConfirmadaNemParaPacienteSemAparelho() {
        Long cedo = dose(tratamentoId, AGORA.minusMinutes(1), "PENDENTE");
        Long confirmada = dose(tratamentoId, AGORA.minusMinutes(5), "CONFIRMADA_NO_HORARIO");
        Long semAparelho = dose(tratamento(medicamento(paciente(null))), AGORA.minusMinutes(5), "PENDENTE");

        assertThat(lembrar.executar().lembretes()).isZero();

        assertThat(push.envios).isEmpty();
        assertThat(eventosDa(cedo)).isEmpty();
        assertThat(eventosDa(confirmada)).isEmpty();
        assertThat(eventosDa(semAparelho)).isEmpty();

        // Aos 2 minutos, a primeira passa a ser lembrada
        relogio.ajustar(AGORA.plusMinutes(1));
        assertThat(lembrar.executar().lembretes()).isEqualTo(1);
        assertThat(eventosDa(cedo)).containsExactly("LEMBRETE_PACIENTE:ENVIADO:1");
    }

    @Test
    void paraNoAvisoAoCuidadorConformeAToleranciaDeCadaCuidador() {
        // Tolerância padrão (30): a dose das 08:30 chega ao aviso agora (09:00) e não é mais lembrada
        Long noAviso = dose(tratamentoId, AGORA.minusMinutes(30), "PENDENTE");
        // Outro cuidador com tolerância de 60: a dose das 08:30 ainda está no lembrete
        Long outroCuidador = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, "
                + "consentimento_lgpd, data_consentimento) VALUES ('Outro', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "lembrete-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        jdbc.update("INSERT INTO configuracao_alerta (cuidador_id, tolerancia_minutos) VALUES (?, 60)", outroCuidador);
        Long pacienteDoOutro = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id, token_fcm, "
                + "token_atualizado_em) VALUES ('Helena', ?, ?, 'token-da-helena', now()) RETURNING id", Long.class,
                LocalDate.of(1950, 1, 1), outroCuidador);
        Long comToleranciaMaior = dose(tratamento(medicamento(pacienteDoOutro)), AGORA.minusMinutes(30), "PENDENTE");
        try {
            assertThat(lembrar.executar().lembretes()).isEqualTo(1);

            assertThat(eventosDa(noAviso)).isEmpty();
            assertThat(eventosDa(comToleranciaMaior)).containsExactly("LEMBRETE_PACIENTE:ENVIADO:1");
            assertThat(push.envios).extracting(Envio::token).containsExactly("token-da-helena");
        } finally {
            limparDoses();
            jdbc.update("DELETE FROM tratamento WHERE medicamento_id IN (SELECT id FROM medicamento WHERE paciente_id = ?)",
                    pacienteDoOutro);
            jdbc.update("DELETE FROM medicamento WHERE paciente_id = ?", pacienteDoOutro);
            jdbc.update("DELETE FROM paciente WHERE id = ?", pacienteDoOutro);
            jdbc.update("DELETE FROM configuracao_alerta WHERE cuidador_id = ?", outroCuidador);
            jdbc.update("DELETE FROM cuidador WHERE id = ?", outroCuidador);
        }
    }

    @Test
    void avisoAoCuidadorELembreteAoPacienteDaMesmaDoseConvivemUmDeCadaTipo() {
        Long dose = dose(tratamentoId, AGORA.minusMinutes(5), "PENDENTE");
        eventos.criarSeAusente(dose, TipoEventoNotificacao.AVISO_CUIDADOR).orElseThrow();

        assertThat(lembrar.executar().lembretes()).isEqualTo(1);
        assertThat(eventos.criarSeAusente(dose, TipoEventoNotificacao.LEMBRETE_PACIENTE)).isEmpty();
        assertThat(eventos.criarSeAusente(dose, TipoEventoNotificacao.AVISO_CUIDADOR)).isEmpty();

        assertThat(eventosDa(dose)).containsExactly("AVISO_CUIDADOR:PENDENTE:0", "LEMBRETE_PACIENTE:ENVIADO:1");
    }

    @Test
    void tokenInvalidoApagaOTokenDoPacienteEEncerraOLembrete() {
        Long dose = dose(tratamentoId, AGORA.minusMinutes(5), "PENDENTE");
        push.resposta = ResultadoEnvioPush.TOKEN_INVALIDO;

        lembrar.executar();

        assertThat(eventosDa(dose)).containsExactly("LEMBRETE_PACIENTE:FALHA:1");
        assertThat(jdbc.queryForObject("SELECT motivo_falha FROM evento_notificacao WHERE dose_id = ?", String.class, dose))
                .isEqualTo("TOKEN_INVALIDO");
        assertThat(jdbc.queryForObject("SELECT token_fcm FROM paciente WHERE id = ?", String.class, pacienteId)).isNull();
        // Sem token, a dose seguinte nem é escolhida
        dose(tratamentoId, AGORA.minusMinutes(4), "PENDENTE");
        push.envios.clear();
        assertThat(lembrar.executar().lembretes()).isZero();
        assertThat(push.envios).isEmpty();
    }

    @Test
    void falhaTemporariaEhReenviadaPelaRetentativaDentroDaJanela() {
        Long dose = dose(tratamentoId, AGORA.minusMinutes(5), "PENDENTE");
        push.resposta = ResultadoEnvioPush.FALHA_TEMPORARIA;
        lembrar.executar();
        assertThat(eventosDa(dose)).containsExactly("LEMBRETE_PACIENTE:PENDENTE:1");

        // Antes do intervalo base (5 min), nada; depois, a segunda tentativa vai e é aceita
        push.resposta = ResultadoEnvioPush.ENVIADO;
        relogio.ajustar(AGORA.plusMinutes(4));
        assertThat(reenviar.executar().tentativas()).isZero();
        relogio.ajustar(AGORA.plusMinutes(5));
        assertThat(reenviar.executar().enviadas()).isEqualTo(1);

        assertThat(eventosDa(dose)).containsExactly("LEMBRETE_PACIENTE:ENVIADO:2");
        assertThat(push.envios).hasSize(2);
        assertThat(push.envios.get(1).opcoes().canalAndroid()).isEqualTo("lembrete-dose");
    }

    @Test
    void retentativaDesisteQuandoADoseChegaAoAvisoOuEhConfirmada() {
        Long atrasada = dose(tratamentoId, AGORA.minusMinutes(27), "PENDENTE");
        Long confirmadaDepois = dose(tratamentoId, AGORA.minusMinutes(5), "PENDENTE");
        push.resposta = ResultadoEnvioPush.FALHA_TEMPORARIA;
        lembrar.executar();
        jdbc.update("UPDATE dose SET status = 'CONFIRMADA_NO_HORARIO', hora_confirmacao = ?, confirmado_por = 'PACIENTE' "
                + "WHERE id = ?", AGORA.plusMinutes(1), confirmadaDepois);
        push.envios.clear();
        push.resposta = ResultadoEnvioPush.ENVIADO;

        // 09:05: a dose das 08:33 chegou ao aviso ao cuidador às 09:03
        relogio.ajustar(AGORA.plusMinutes(5));
        ReenviarAvisosPendentesUseCase.Resultado resultado = reenviar.executar();

        assertThat(resultado.canceladas()).isEqualTo(2);
        assertThat(push.envios).isEmpty();
        assertThat(jdbc.queryForObject("SELECT motivo_falha FROM evento_notificacao WHERE dose_id = ?", String.class, atrasada))
                .isEqualTo("FORA_DA_JANELA");
        assertThat(jdbc.queryForObject("SELECT motivo_falha FROM evento_notificacao WHERE dose_id = ?", String.class,
                confirmadaDepois)).isEqualTo("DOSE_JA_REGISTRADA");
    }

    // ---- apoio --------------------------------------------------------------------------------------------------

    private List<String> eventosDa(Long dose) {
        return jdbc.queryForList("SELECT tipo || ':' || status || ':' || tentativas FROM evento_notificacao "
                + "WHERE dose_id = ? ORDER BY tipo", String.class, dose);
    }

    private void limparDoses() {
        for (Long dose : DOSES_DO_TESTE) {
            jdbc.update("DELETE FROM evento_notificacao WHERE dose_id = ?", dose);
            jdbc.update("DELETE FROM dose WHERE id = ?", dose);
        }
        DOSES_DO_TESTE.clear();
    }

    private Long paciente(String token) {
        return jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id, token_fcm, token_atualizado_em) "
                        + "VALUES (?, ?, ?, ?, CASE WHEN CAST(? AS VARCHAR) IS NULL THEN NULL ELSE now() END) RETURNING id", Long.class,
                "Paciente " + UUID.randomUUID(), LocalDate.of(1945, 3, 12), cuidadorId, token, token);
    }

    private Long medicamento(Long paciente) {
        return jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, paciente);
    }

    private Long tratamento(Long medicamento) {
        return jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamento, AGORA.toLocalDate());
    }

    private Long dose(Long tratamento, LocalDateTime horario, String status) {
        boolean registrada = !"PENDENTE".equals(status);
        Long id = jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status, hora_confirmacao, "
                        + "confirmado_por) VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, tratamento, horario, status,
                registrada ? horario : null, registrada ? "PACIENTE" : null);
        DOSES_DO_TESTE.add(id);
        return id;
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
}
