package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Token de push do aparelho do paciente (lembrete de dose do servidor), de ponta a ponta: segurança, controller, use
 * cases e PostgreSQL real. O paciente vem sempre do JWT; o token some em todos os fluxos de saída (sair da conta,
 * desconexão do aparelho, desvinculação e exclusão da conta). Cada teste roda numa transação desfeita no final.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class TokenDoPacienteApiTest {

    private static final String TOKEN_DO_PACIENTE = "/api/pacientes/me/fcm-token";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDateTime agora;
    private Long cuidadorA;
    private Long pacienteA;
    private Long cuidadorB;
    private Long pacienteB;

    @BeforeEach
    void criarDados() {
        agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        cuidadorA = cuidador("Ana Souza");
        pacienteA = paciente(cuidadorA, "José Souza");
        cuidadorB = cuidador("Bruno Lima");
        pacienteB = paciente(cuidadorB, "Helena Lima");
    }

    @Test
    void pacienteRegistraOTokenDoAparelhoComADataDoRegistro() throws Exception {
        mvc.perform(comoPaciente(put(TOKEN_DO_PACIENTE), pacienteA).content(corpo("token-do-celular-do-jose")))
                .andExpect(status().isNoContent());

        assertThat(tokenDe(pacienteA)).isEqualTo("token-do-celular-do-jose");
        OffsetDateTime registradoEm = jdbc.queryForObject("SELECT token_atualizado_em FROM paciente WHERE id = ?",
                OffsetDateTime.class, pacienteA);
        assertThat(registradoEm).isNotNull();
        assertThat(registradoEm.toInstant()).isBetween(clock.instant().minusSeconds(60), clock.instant().plusSeconds(1));
    }

    @Test
    void registrarDeNovoTrocaOTokenEApagarEhIdempotente() throws Exception {
        registrar(pacienteA, "token-antigo");
        registrar(pacienteA, "token-novo");
        assertThat(tokenDe(pacienteA)).isEqualTo("token-novo");

        mvc.perform(comoPaciente(delete(TOKEN_DO_PACIENTE), pacienteA)).andExpect(status().isNoContent());
        mvc.perform(comoPaciente(delete(TOKEN_DO_PACIENTE), pacienteA)).andExpect(status().isNoContent());

        assertThat(tokenDe(pacienteA)).isNull();
        assertThat(jdbc.queryForObject("SELECT token_atualizado_em FROM paciente WHERE id = ?", OffsetDateTime.class,
                pacienteA)).isNull();
    }

    @Test
    void oPacienteVemDoJwtEOCorpoNaoEscolheOutroPaciente() throws Exception {
        registrar(pacienteB, "token-da-helena");

        // Um campo a mais no corpo (pacienteId de outro cuidador) é ignorado: só o paciente do JWT muda
        mvc.perform(comoPaciente(put(TOKEN_DO_PACIENTE), pacienteA)
                        .content("{\"token\":\"token-do-jose\",\"pacienteId\":" + pacienteB + "}"))
                .andExpect(status().isNoContent());
        mvc.perform(comoPaciente(delete(TOKEN_DO_PACIENTE), pacienteA).content("{\"pacienteId\":" + pacienteB + "}"))
                .andExpect(status().isNoContent());

        assertThat(tokenDe(pacienteA)).isNull();
        assertThat(tokenDe(pacienteB)).isEqualTo("token-da-helena");
    }

    @Test
    void umAparelhoPertenceAUmPacientePorVezEOCuidadorNaoEhTocado() throws Exception {
        jdbc.update("UPDATE cuidador SET fcm_token = ? WHERE id = ?", "mesmo-celular", cuidadorA);
        registrar(pacienteB, "mesmo-celular");

        registrar(pacienteA, "mesmo-celular");

        assertThat(tokenDe(pacienteA)).isEqualTo("mesmo-celular");
        assertThat(tokenDe(pacienteB)).isNull();
        // O mesmo celular pode ter os dois perfis: o token do cuidador continua
        assertThat(jdbc.queryForObject("SELECT fcm_token FROM cuidador WHERE id = ?", String.class, cuidadorA))
                .isEqualTo("mesmo-celular");
    }

    @Test
    void semTokenResponde401ComPerfilErradoResponde403ETokenInvalidoResponde400() throws Exception {
        mvc.perform(put(TOKEN_DO_PACIENTE).contentType(MediaType.APPLICATION_JSON).content(corpo("x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mvc.perform(delete(TOKEN_DO_PACIENTE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        mvc.perform(comoCuidador(put(TOKEN_DO_PACIENTE), cuidadorA).content(corpo("x")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mvc.perform(comoCuidador(delete(TOKEN_DO_PACIENTE), cuidadorA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));

        mvc.perform(comoPaciente(put(TOKEN_DO_PACIENTE), pacienteA).content(corpo("   ")))
                .andExpect(status().isBadRequest());
        mvc.perform(comoPaciente(put(TOKEN_DO_PACIENTE), pacienteA).content(corpo("x".repeat(513))))
                .andExpect(status().isBadRequest());
        assertThat(tokenDe(pacienteA)).isNull();
    }

    @Test
    void pacienteQueNaoExisteMaisResponde401() throws Exception {
        mvc.perform(comoPaciente(put(TOKEN_DO_PACIENTE), Long.MAX_VALUE).content(corpo("token")))
                .andExpect(status().isUnauthorized());
    }

    // ---- o token some em todos os fluxos de saída -------------------------------------------------------------------

    @Test
    void desconectarOAparelhoApagaOTokenEOAparelhoNaoConsegueRegistrarDeNovo() throws Exception {
        registrar(pacienteA, "token-do-jose");
        registrar(pacienteB, "token-da-helena");
        String jwtAntigo = tokens.generateToken(pacienteA, Role.PACIENTE);

        mvc.perform(comoCuidador(post("/api/pacientes/" + pacienteA + "/desconectar-aparelho"), cuidadorA))
                .andExpect(status().isNoContent());

        assertThat(tokenDe(pacienteA)).isNull();
        assertThat(tokenDe(pacienteB)).isEqualTo("token-da-helena");
        // A sessão antiga não volta a registrar o aparelho
        mvc.perform(put(TOKEN_DO_PACIENTE).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtAntigo)
                        .contentType(MediaType.APPLICATION_JSON).content(corpo("token-do-jose")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        assertThat(tokenDe(pacienteA)).isNull();
    }

    @Test
    void outroCuidadorNaoDesconectaNemApagaOToken() throws Exception {
        registrar(pacienteA, "token-do-jose");

        mvc.perform(comoCuidador(post("/api/pacientes/" + pacienteA + "/desconectar-aparelho"), cuidadorB))
                .andExpect(status().isNotFound());

        assertThat(tokenDe(pacienteA)).isEqualTo("token-do-jose");
    }

    @Test
    void desvincularOPacienteApagaOTokenJuntoComOPaciente() throws Exception {
        registrar(pacienteA, "token-do-jose");
        registrar(pacienteB, "token-da-helena");

        mvc.perform(comoCuidador(delete("/api/pacientes/" + pacienteA), cuidadorA)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForList("SELECT token_fcm FROM paciente WHERE token_fcm = 'token-do-jose'", String.class))
                .isEmpty();
        assertThat(tokenDe(pacienteB)).isEqualTo("token-da-helena");
    }

    @Test
    void excluirAContaDoCuidadorApagaOsTokensDosPacientesDele() throws Exception {
        Long outroPacienteDeA = paciente(cuidadorA, "Maria Souza");
        registrar(pacienteA, "token-do-jose");
        registrar(outroPacienteDeA, "token-da-maria");
        registrar(pacienteB, "token-da-helena");

        mvc.perform(comoCuidador(delete("/api/cuidadores/me"), cuidadorA)).andExpect(status().isNoContent());

        List<String> restantes = jdbc.queryForList("SELECT token_fcm FROM paciente WHERE token_fcm IN "
                + "('token-do-jose', 'token-da-maria', 'token-da-helena')", String.class);
        assertThat(restantes).containsExactly("token-da-helena");
    }

    @Test
    void saidaAutorizadaPeloCuidadorApagaOToken() throws Exception {
        registrar(pacienteA, "token-do-jose");
        String resposta = mvc.perform(comoPaciente(post("/api/pacientes/me/pedido-desconexao"), pacienteA))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long pedido = Long.parseLong(resposta.replaceAll(".*\"id\":(\\d+).*", "$1"));

        mvc.perform(comoCuidador(put("/api/pacientes/" + pacienteA + "/pedidos-desconexao/" + pedido + "/autorizar"),
                cuidadorA)).andExpect(status().isOk());

        assertThat(tokenDe(pacienteA)).isNull();
    }

    // ---- apoio --------------------------------------------------------------------------------------------------

    private void registrar(Long pacienteId, String token) throws Exception {
        mvc.perform(comoPaciente(put(TOKEN_DO_PACIENTE), pacienteId).content(corpo(token)))
                .andExpect(status().isNoContent());
    }

    private String tokenDe(Long pacienteId) {
        List<Map<String, Object>> linhas = jdbc.queryForList("SELECT token_fcm FROM paciente WHERE id = ?", pacienteId);
        return linhas.isEmpty() ? null : (String) linhas.get(0).get("token_fcm");
    }

    private static String corpo(String token) {
        return "{\"token\":\"" + token + "\"}";
    }

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder requisicao, Long cuidadorId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private MockHttpServletRequestBuilder comoPaciente(MockHttpServletRequestBuilder requisicao, Long pacienteId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(pacienteId, Role.PACIENTE))
                .contentType(MediaType.APPLICATION_JSON);
    }

    private Long cuidador(String nome) {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                        + "data_consentimento) VALUES (?, ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                nome, "token-paciente-" + UUID.randomUUID() + "@teste.com", agora, agora);
    }

    private Long paciente(Long cuidadorId, String nome) {
        return jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidadorId);
    }
}
