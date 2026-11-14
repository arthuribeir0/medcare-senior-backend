package com.medcare.senior.infrastructure.persistence;

import com.medcare.senior.application.service.DesconexaoDoAparelhoService;
import com.medcare.senior.application.usecase.DecidirPedidoDesconexaoUseCase;
import com.medcare.senior.application.usecase.PedirDesconexaoUseCase;
import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import com.medcare.senior.domain.exception.PedidoDesconexaoEncerradoException;
import com.medcare.senior.infrastructure.notificacao.LogNotificadorDePedidoDeDesconexaoAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.CodigoVinculoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.PacienteRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.adapter.PedidoDesconexaoRepositoryAdapter;
import com.medcare.senior.infrastructure.persistence.mapper.CodigoVinculoMapper;
import com.medcare.senior.infrastructure.persistence.mapper.PacienteMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
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
 * Pedidos e decisões simultâneos, em transações reais (commit) contra o PostgreSQL: o índice único parcial garante um
 * só pedido pendente, e o UPDATE condicional deixa só uma decisão valer. Roda sem a transação de teste e apaga o que
 * criou.
 */
@DataJpaTest(properties = "spring.jpa.show-sql=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PedidoDesconexaoRepositoryAdapter.class, PacienteRepositoryAdapter.class, PacienteMapper.class,
        CodigoVinculoRepositoryAdapter.class, CodigoVinculoMapper.class, DesconexaoDoAparelhoService.class,
        DecidirPedidoDesconexaoUseCase.class, PedirDesconexaoUseCase.class, LogNotificadorDePedidoDeDesconexaoAdapter.class,
        DesconexaoConcorrentePostgresTest.Configuracao.class})
class DesconexaoConcorrentePostgresTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // Em 2000: nenhum dado de desenvolvimento do banco local interfere
    private static final LocalDateTime AGORA = LocalDateTime.of(2000, 1, 10, 8, 0);

    @TestConfiguration
    static class Configuracao {
        @Bean
        Clock clock() {
            return Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        }
    }

    @Autowired
    private DecidirPedidoDesconexaoUseCase decidirUseCase;

    @Autowired
    private PedirDesconexaoUseCase pedirUseCase;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long pacienteId;

    @BeforeEach
    void criar() {
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador desconexão', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "desconexao-" + UUID.randomUUID() + "@teste.com", AGORA, AGORA);
        pacienteId = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Paciente " + UUID.randomUUID(), LocalDate.of(1945, 3, 12), cuidadorId);
    }

    @AfterEach
    void limpar() {
        jdbc.update("DELETE FROM pedido_desconexao WHERE paciente_id = ?", pacienteId);
        jdbc.update("DELETE FROM paciente WHERE id = ?", pacienteId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);
    }

    private Long pedidoPendente() {
        return jdbc.queryForObject("INSERT INTO pedido_desconexao (paciente_id, status, criado_em, expira_em) "
                + "VALUES (?, 'PENDENTE', ?, ?) RETURNING id", Long.class, pacienteId, AGORA.minusHours(1), AGORA.plusHours(23));
    }

    @RepeatedTest(3)
    void autorizarERecusarAoMesmoTempoSoUmaDecisaoVale() throws Exception {
        Long pedido = pedidoPendente();

        List<Boolean> resultados = emParalelo(
                () -> decidir(() -> decidirUseCase.autorizar(pacienteId, pedido, cuidadorId)),
                () -> decidir(() -> decidirUseCase.recusar(pacienteId, pedido, cuidadorId)));

        assertThat(resultados).containsExactlyInAnyOrder(true, false);
        String status = jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, pedido);
        boolean desconectado = jdbc.queryForObject("SELECT sessao_valida_desde IS NOT NULL FROM paciente WHERE id = ?",
                Boolean.class, pacienteId);
        // Só a autorização vencedora desconecta o aparelho
        assertThat(desconectado).isEqualTo(StatusPedidoDesconexao.AUTORIZADO.name().equals(status));
    }

    @RepeatedTest(3)
    void duasAutorizacoesAoMesmoTempoDevolvemAutorizadoSemErro() throws Exception {
        Long pedido = pedidoPendente();

        List<Boolean> resultados = emParalelo(
                () -> decidir(() -> decidirUseCase.autorizar(pacienteId, pedido, cuidadorId)),
                () -> decidir(() -> decidirUseCase.autorizar(pacienteId, pedido, cuidadorId)));

        assertThat(resultados).containsExactly(true, true);
        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, pedido))
                .isEqualTo("AUTORIZADO");
    }

    @RepeatedTest(3)
    void doisPedidosAoMesmoTempoGravamUmSoPendente() throws Exception {
        List<Boolean> criados = emParalelo(
                () -> pedirUseCase.executar(pacienteId).criado(),
                () -> pedirUseCase.executar(pacienteId).criado());

        assertThat(criados).containsExactlyInAnyOrder(true, false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedido_desconexao WHERE paciente_id = ?", Long.class, pacienteId))
                .isEqualTo(1L);
    }

    private static boolean decidir(Runnable decisao) {
        try {
            decisao.run();
            return true;
        } catch (PedidoDesconexaoEncerradoException e) {
            return false;
        }
    }

    @SafeVarargs
    private static List<Boolean> emParalelo(Callable<Boolean>... tarefas) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(tarefas.length);
        try {
            List<Future<Boolean>> futuros = new ArrayList<>();
            for (Callable<Boolean> tarefa : tarefas) {
                futuros.add(executor.submit(() -> {
                    largada.await();
                    return tarefa.call();
                }));
            }
            largada.countDown();
            List<Boolean> resultados = new ArrayList<>();
            for (Future<Boolean> futuro : futuros) {
                resultados.add(futuro.get(30, TimeUnit.SECONDS));
            }
            return resultados;
        } finally {
            executor.shutdownNow();
        }
    }
}
