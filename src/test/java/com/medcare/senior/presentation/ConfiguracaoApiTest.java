package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tolerância por cuidador (RN-009) pela API, contra o PostgreSQL real: GET/PUT /api/configuracoes/tolerancia, limites
 * da validação, papéis e o reflexo na janela das doses. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class ConfiguracaoApiTest {

    private static final String ROTA = "/api/configuracoes/tolerancia";
    private static final DateTimeFormatter ATE_O_MINUTO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDateTime agora;
    private Long cuidadorId;
    private Long idosoId;

    @BeforeEach
    void criarDados() {
        agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador tolerância', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "tolerancia-" + UUID.randomUUID() + "@teste.com", agora, agora);
        idosoId = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES ('Maria', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1945, 3, 12), cuidadorId);
    }

    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder requisicao, Long id, Role papel) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(id, papel));
    }

    private MockHttpServletRequestBuilder putTolerancia(String corpo) {
        return como(put(ROTA).contentType(MediaType.APPLICATION_JSON).content(corpo), cuidadorId, Role.CUIDADOR);
    }

    @Test
    void semConfiguracaoDevolveAPadrao() throws Exception {
        mvc.perform(como(get(ROTA), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toleranciaMinutos").value(30));
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 120})
    void aceitaOsLimitesInclusivos(int minutos) throws Exception {
        mvc.perform(putTolerancia("{\"toleranciaMinutos\": " + minutos + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toleranciaMinutos").value(minutos));
        mvc.perform(como(get(ROTA), cuidadorId, Role.CUIDADOR))
                .andExpect(jsonPath("$.toleranciaMinutos").value(minutos));
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 121, 0, -1})
    void recusaForaDosLimitesComValidacaoNoCampo(int minutos) throws Exception {
        mvc.perform(putTolerancia("{\"toleranciaMinutos\": " + minutos + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(1)))
                .andExpect(jsonPath("$.campos[0].campo").value("toleranciaMinutos"))
                .andExpect(jsonPath("$.campos[0].mensagem").value("A tolerância deve ficar entre 5 e 120 minutos"));
        // Nada foi gravado
        mvc.perform(como(get(ROTA), cuidadorId, Role.CUIDADOR)).andExpect(jsonPath("$.toleranciaMinutos").value(30));
    }

    @Test
    void toleranciaAusenteEhValidacaoECorpoAusenteOuInvalidoEhCorpoInvalido() throws Exception {
        mvc.perform(putTolerancia("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("toleranciaMinutos"));
        mvc.perform(como(put(ROTA).contentType(MediaType.APPLICATION_JSON), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));
        mvc.perform(putTolerancia("{\"toleranciaMinutos\": \"trinta\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));
    }

    @Test
    void gravarDuasVezesTrocaOValor() throws Exception {
        mvc.perform(putTolerancia("{\"toleranciaMinutos\": 45}")).andExpect(status().isOk());
        mvc.perform(putTolerancia("{\"toleranciaMinutos\": 15}")).andExpect(status().isOk());

        mvc.perform(como(get(ROTA), cuidadorId, Role.CUIDADOR)).andExpect(jsonPath("$.toleranciaMinutos").value(15));
    }

    @Test
    void idosoNaoAcessaETokenAusenteEh401() throws Exception {
        mvc.perform(como(get(ROTA), idosoId, Role.IDOSO)).andExpect(status().isForbidden());
        mvc.perform(como(put(ROTA).contentType(MediaType.APPLICATION_JSON).content("{\"toleranciaMinutos\": 30}"), idosoId,
                Role.IDOSO)).andExpect(status().isForbidden());
        mvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
        mvc.perform(put(ROTA).contentType(MediaType.APPLICATION_JSON).content("{\"toleranciaMinutos\": 30}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cuidadorDeContaExcluidaRecebeSessaoInvalida() throws Exception {
        jdbc.update("DELETE FROM idoso WHERE id = ?", idosoId);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorId);

        mvc.perform(como(get(ROTA), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        mvc.perform(putTolerancia("{\"toleranciaMinutos\": 30}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
    }

    @Test
    void dosesDoIdosoRefletemAToleranciaDoCuidador() throws Exception {
        Long medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, idosoId);
        Long tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, agora.toLocalDate().minusDays(1));
        jdbc.update("INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (?, ?, 'PENDENTE')",
                tratamentoId, agora);
        String avisoEm = agora.plusMinutes(60).format(ATE_O_MINUTO);

        mvc.perform(putTolerancia("{\"toleranciaMinutos\": 60}")).andExpect(status().isOk());

        mvc.perform(como(get("/api/idosos/" + idosoId + "/doses"), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toleranciaMinutos").value(60))
                .andExpect(jsonPath("$[0].janela.avisoEm", startsWith(avisoEm)));
        mvc.perform(como(get("/api/idosos/me/doses"), idosoId, Role.IDOSO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].toleranciaMinutos").value(60))
                .andExpect(jsonPath("$[0].janela.avisoEm", startsWith(avisoEm)));
    }
}
