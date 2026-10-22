package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Relatório diário (UC-008) pela API, contra o PostgreSQL real: totais, fórmula, dia padrão no fuso da aplicação,
 * idoso alheio, papéis e parâmetros inválidos. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class RelatorioApiTest {

    private static final String ROTA = "/api/relatorios/diario";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDate hoje;
    private Long cuidadorId;
    private Long idosoId;
    private Long tratamentoId;

    @BeforeEach
    void criarDados() {
        LocalDateTime agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        hoje = agora.toLocalDate();
        cuidadorId = cuidador();
        idosoId = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES ('José Souza', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1945, 3, 12), cuidadorId);
        Long medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, idosoId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, hoje.minusDays(10));
    }

    private Long cuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador relatório', ?, 'hash', now(), true, now()) RETURNING id",
                Long.class, "relatorio-" + UUID.randomUUID() + "@teste.com");
    }

    private void dose(LocalDate dia, int hora, String status) {
        boolean confirmada = !"PENDENTE".equals(status) && !"NAO_TOMADA".equals(status);
        jdbc.update("INSERT INTO dose (tratamento_id, horario_programado, hora_confirmacao, status, confirmado_por) "
                + "VALUES (?, ?, ?, ?, ?)", tratamentoId, dia.atTime(hora, 0), confirmada ? dia.atTime(hora, 10) : null,
                status, confirmada ? "IDOSO" : null);
    }

    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder requisicao, Long id, Role papel) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(id, papel));
    }

    @Test
    void misturaDeStatusComTotaisEAderencia() throws Exception {
        LocalDate dia = hoje.minusDays(1);
        dose(dia, 7, "CONFIRMADA_NO_HORARIO");
        dose(dia, 9, "CONFIRMADA_NO_HORARIO");
        dose(dia, 12, "CONFIRMADA_COM_ATRASO");
        dose(dia, 18, "NAO_TOMADA");
        // Dose de outro dia não entra
        dose(dia.minusDays(1), 8, "NAO_TOMADA");

        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()).param("data", dia.toString()), cuidadorId,
                        Role.CUIDADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idoso.id").value(idosoId))
                .andExpect(jsonPath("$.idoso.nome").value("José Souza"))
                .andExpect(jsonPath("$.data").value(dia.toString()))
                .andExpect(jsonPath("$.totais.total").value(4))
                .andExpect(jsonPath("$.totais.confirmadasNoHorario").value(2))
                .andExpect(jsonPath("$.totais.confirmadasComAtraso").value(1))
                .andExpect(jsonPath("$.totais.naoTomadas").value(1))
                .andExpect(jsonPath("$.totais.pendentes").value(0))
                .andExpect(jsonPath("$.totais.aderenciaPercentual").value(75.0))
                .andExpect(jsonPath("$.doses", hasSize(4)))
                .andExpect(jsonPath("$.doses[0].horarioProgramado").value(dia.atTime(7, 0) + ":00"))
                .andExpect(jsonPath("$.doses[3].status").value("NAO_TOMADA"))
                .andExpect(jsonPath("$.doses[0].janela.abreEm", notNullValue()))
                .andExpect(jsonPath("$.doses[0].confirmabilidade").value("JA_REGISTRADA"));
    }

    @Test
    void semDataUsaHojeNoFusoDaAplicacaoESoPendentesNaoTemAderencia() throws Exception {
        dose(hoje, 23, "PENDENTE");

        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(hoje.toString()))
                .andExpect(jsonPath("$.totais.total").value(1))
                .andExpect(jsonPath("$.totais.pendentes").value(1))
                .andExpect(jsonPath("$.totais.aderenciaPercentual").value(nullValue()));
    }

    @Test
    void diaSemDosesDevolveListaVazia() throws Exception {
        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()).param("data", "2001-01-01"), cuidadorId,
                        Role.CUIDADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doses", hasSize(0)))
                .andExpect(jsonPath("$.totais.total").value(0))
                .andExpect(jsonPath("$.totais.aderenciaPercentual").value(nullValue()));
    }

    @Test
    void idosoDeOutroCuidadorEhNaoEncontrado() throws Exception {
        Long outro = cuidador();

        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()), outro, Role.CUIDADOR))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void parametrosInvalidosSao400() throws Exception {
        mvc.perform(como(get(ROTA), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo", notNullValue()));
        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()).param("data", "18/10/2026"), cuidadorId,
                        Role.CUIDADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("data"));
        mvc.perform(como(get(ROTA).param("idosoId", "abc"), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
    }

    @Test
    void papeisEContaExcluida() throws Exception {
        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()), idosoId, Role.IDOSO))
                .andExpect(status().isForbidden());
        mvc.perform(get(ROTA).param("idosoId", idosoId.toString())).andExpect(status().isUnauthorized());

        Long excluido = cuidador();
        jdbc.update("DELETE FROM cuidador WHERE id = ?", excluido);
        mvc.perform(como(get(ROTA).param("idosoId", idosoId.toString()), excluido, Role.CUIDADOR))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
    }
}
