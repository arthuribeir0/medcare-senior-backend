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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mensagens (UC-010) pela API, contra o PostgreSQL real, nos dois papéis: envio, recusas com o código de cada caso
 * (400 VALIDACAO, 403 ACESSO_NEGADO, 404, 401 SESSAO_INVALIDA), conversa paginada, lista de conversas, marcar como
 * lida e contador. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class MensagemApiTest {

    private static final String ROTA = "/api/mensagens";
    private static final LocalDateTime T0 = LocalDateTime.of(2000, 1, 10, 8, 0);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long pacienteId;
    private Long outroPacienteDoCuidador;
    private Long pacienteDeOutroCuidador;

    @BeforeEach
    void criarDados() {
        cuidadorId = cuidador();
        pacienteId = paciente(cuidadorId, "Maria");
        outroPacienteDoCuidador = paciente(cuidadorId, "João");
        pacienteDeOutroCuidador = paciente(cuidador(), "Alheio");
    }

    private Long cuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador mensagens', ?, 'hash', now(), true, now()) RETURNING id",
                Long.class, "mensagens-api-" + UUID.randomUUID() + "@teste.com");
    }

    private Long paciente(Long cuidador, String nome) {
        return jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidador);
    }

    private void mensagem(Long paciente, String remetente, String conteudo, LocalDateTime quando) {
        jdbc.update("INSERT INTO mensagem (paciente_id, remetente, conteudo, criada_em) VALUES (?, ?, ?, ?)", paciente,
                remetente, conteudo, quando);
    }

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR));
    }

    private MockHttpServletRequestBuilder comoPaciente(MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(pacienteId, Role.PACIENTE));
    }

    private static MockHttpServletRequestBuilder enviar(String json) {
        return post(ROTA).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static void erro(ResultActions resultado, int status, String codigo) throws Exception {
        resultado.andExpect(status().is(status)).andExpect(jsonPath("$.codigo").value(codigo));
    }

    // --- Envio ----------------------------------------------------------------------------------------------------

    @Test
    void cuidadorEnviaParaUmPacienteSeuEPacienteEnviaParaOCuidador() throws Exception {
        mvc.perform(comoCuidador(enviar("{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"  Bom dia, Maria!  \"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.remetente").value("CUIDADOR"))
                .andExpect(jsonPath("$.conteudo").value("Bom dia, Maria!"))
                .andExpect(jsonPath("$.criadaEm", notNullValue()))
                .andExpect(jsonPath("$.lidaEm", nullValue()));
        // O paciente não precisa (nem pode) escolher a conversa: é sempre a dele
        mvc.perform(comoPaciente(enviar("{\"conteudo\": \"Bom dia!\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remetente").value("PACIENTE"));
        mvc.perform(comoPaciente(enviar("{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"De novo\"}")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mensagem WHERE paciente_id = ?", Long.class, pacienteId)).isEqualTo(3);
    }

    @Test
    void conteudoVazioSoEspacosAusenteOuMaiorQue500Eh400NoCampoConteudo() throws Exception {
        for (String corpo : List.of("{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"\"}",
                "{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"   \\n \"}",
                "{\"pacienteId\": " + pacienteId + "}",
                "{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"" + "a".repeat(501) + "\"}")) {
            erro(mvc.perform(comoCuidador(enviar(corpo))), 400, "VALIDACAO");
            mvc.perform(comoCuidador(enviar(corpo))).andExpect(jsonPath("$.campos[0].campo").value("conteudo"));
        }
        erro(mvc.perform(comoPaciente(enviar("{\"conteudo\": \"\"}"))), 400, "VALIDACAO");
        // Exatamente 500 passa
        mvc.perform(comoCuidador(enviar("{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"" + "a".repeat(500) + "\"}")))
                .andExpect(status().isCreated());
    }

    @Test
    void cuidadorSemPacienteEh400ECorpoIlegivelEhCorpoInvalido() throws Exception {
        erro(mvc.perform(comoCuidador(enviar("{\"conteudo\": \"Oi\"}"))), 400, "VALIDACAO");
        mvc.perform(comoCuidador(enviar("{\"conteudo\": \"Oi\"}"))).andExpect(jsonPath("$.campos[0].campo").value("pacienteId"));
        erro(mvc.perform(comoCuidador(enviar("{nao e json"))), 400, "CORPO_INVALIDO");
    }

    // --- Acesso ---------------------------------------------------------------------------------------------------

    @Test
    void cuidadorNaoAcessaPacienteDeOutroCuidadorEmNenhumaRota() throws Exception {
        erro(mvc.perform(comoCuidador(enviar("{\"pacienteId\": " + pacienteDeOutroCuidador + ", \"conteudo\": \"Oi\"}"))),
                404, "RECURSO_NAO_ENCONTRADO");
        erro(mvc.perform(comoCuidador(get(ROTA).param("pacienteId", pacienteDeOutroCuidador.toString()))),
                404, "RECURSO_NAO_ENCONTRADO");
        erro(mvc.perform(comoCuidador(put(ROTA + "/lidas").param("pacienteId", pacienteDeOutroCuidador.toString()))),
                404, "RECURSO_NAO_ENCONTRADO");
        erro(mvc.perform(comoCuidador(get(ROTA).param("pacienteId", "999999999"))), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void pacienteInformandoOutroPacienteEhAcessoNegadoEmTodasAsRotas() throws Exception {
        String outro = outroPacienteDoCuidador.toString();
        erro(mvc.perform(comoPaciente(enviar("{\"pacienteId\": " + outro + ", \"conteudo\": \"Oi\"}"))), 403, "ACESSO_NEGADO");
        erro(mvc.perform(comoPaciente(get(ROTA).param("pacienteId", outro))), 403, "ACESSO_NEGADO");
        erro(mvc.perform(comoPaciente(put(ROTA + "/lidas").param("pacienteId", outro))), 403, "ACESSO_NEGADO");
    }

    @Test
    void semTokenEh401EmTodasAsRotas() throws Exception {
        for (MockHttpServletRequestBuilder rota : rotas()) {
            mvc.perform(rota).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tokenDePacienteOuCuidadorExcluidoEh401SessaoInvalidaEmTodasAsRotas() throws Exception {
        Long pacienteExcluido = paciente(cuidadorId, "Excluído");
        jdbc.update("DELETE FROM paciente WHERE id = ?", pacienteExcluido);
        Long cuidadorExcluido = cuidador();
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorExcluido);

        for (MockHttpServletRequestBuilder rota : rotas()) {
            erro(mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(pacienteExcluido, Role.PACIENTE))),
                    401, "SESSAO_INVALIDA");
        }
        for (MockHttpServletRequestBuilder rota : rotas()) {
            erro(mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorExcluido,
                    Role.CUIDADOR))), 401, "SESSAO_INVALIDA");
        }
    }

    private List<MockHttpServletRequestBuilder> rotas() {
        return List.of(
                enviar("{\"pacienteId\": " + pacienteId + ", \"conteudo\": \"Oi\"}"),
                get(ROTA).param("pacienteId", pacienteId.toString()),
                get(ROTA + "/conversas"),
                put(ROTA + "/lidas").param("pacienteId", pacienteId.toString()),
                get(ROTA + "/nao-lidas"));
    }

    @Test
    void outrosMetodosERotasDeMensagensSaoNegados() throws Exception {
        mvc.perform(comoCuidador(delete(ROTA)))
                .andExpect(status().isForbidden());
        mvc.perform(comoPaciente(get(ROTA + "/qualquer"))).andExpect(status().isForbidden());
    }

    // --- Conversa, lidas e contador ----------------------------------------------------------------------------------

    @Test
    void conversaPaginadaDaMaisRecenteNosDoisPapeis() throws Exception {
        for (int i = 1; i <= 25; i++) {
            mensagem(pacienteId, i % 2 == 0 ? "PACIENTE" : "CUIDADOR", "m" + i, T0.plusMinutes(i));
        }
        mensagem(outroPacienteDoCuidador, "CUIDADOR", "outra conversa", T0.plusDays(1));

        mvc.perform(comoCuidador(get(ROTA).param("pacienteId", pacienteId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(20)))
                .andExpect(jsonPath("$.conteudo[0].conteudo").value("m25"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalElementos").value(25))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(comoPaciente(get(ROTA).param("page", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].conteudo", contains("m5", "m4", "m3", "m2", "m1")));
        mvc.perform(comoPaciente(get(ROTA).param("size", "100")))
                .andExpect(jsonPath("$.conteudo", hasSize(25)));
        for (List<String> invalido : List.of(List.of("size", "101"), List.of("size", "0"), List.of("page", "-1"))) {
            erro(mvc.perform(comoPaciente(get(ROTA).param(invalido.get(0), invalido.get(1)))), 400, "VALIDACAO");
        }
        erro(mvc.perform(comoCuidador(get(ROTA))), 400, "VALIDACAO");
    }

    @Test
    void marcarComoLidasSoMudaAsDoOutroLadoEEhIdempotente() throws Exception {
        mensagem(pacienteId, "PACIENTE", "a", T0);
        mensagem(pacienteId, "PACIENTE", "b", T0.plusMinutes(1));
        mensagem(pacienteId, "CUIDADOR", "c", T0.plusMinutes(2));

        mvc.perform(comoCuidador(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(2));
        mvc.perform(comoPaciente(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(1));

        mvc.perform(comoCuidador(put(ROTA + "/lidas").param("pacienteId", pacienteId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(2));
        mvc.perform(comoCuidador(put(ROTA + "/lidas").param("pacienteId", pacienteId.toString())))
                .andExpect(jsonPath("$.marcadas").value(0));

        mvc.perform(comoCuidador(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(0));
        // A do cuidador continua não lida para o paciente
        mvc.perform(comoPaciente(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(1));
        mvc.perform(comoPaciente(get(ROTA)))
                .andExpect(jsonPath("$.conteudo[0].remetente").value("CUIDADOR"))
                .andExpect(jsonPath("$.conteudo[0].lidaEm", nullValue()))
                .andExpect(jsonPath("$.conteudo[1].lidaEm", notNullValue()));

        mvc.perform(comoPaciente(put(ROTA + "/lidas"))).andExpect(jsonPath("$.marcadas").value(1));
        mvc.perform(comoPaciente(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(0));
    }

    @Test
    void conversasDoCuidadorEDoPacienteNoMesmoFormato() throws Exception {
        mensagem(pacienteId, "PACIENTE", "Já tomei", T0.plusMinutes(5));
        mensagem(pacienteId, "CUIDADOR", "Bom dia", T0);
        mensagem(pacienteDeOutroCuidador, "PACIENTE", "não aparece", T0.plusDays(1));

        mvc.perform(comoCuidador(get(ROTA + "/conversas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].pacienteId").value(pacienteId))
                .andExpect(jsonPath("$[0].pacienteNome").value("Maria"))
                .andExpect(jsonPath("$[0].ultimaMensagem.conteudo").value("Já tomei"))
                .andExpect(jsonPath("$[0].ultimaMensagem.remetente").value("PACIENTE"))
                .andExpect(jsonPath("$[0].ultimaMensagem.criadaEm").value(T0.plusMinutes(5) + ":00"))
                .andExpect(jsonPath("$[0].naoLidas").value(1))
                .andExpect(jsonPath("$[1].pacienteId").value(outroPacienteDoCuidador))
                .andExpect(jsonPath("$[1].ultimaMensagem", nullValue()))
                .andExpect(jsonPath("$[1].naoLidas").value(0));
        mvc.perform(comoPaciente(get(ROTA + "/conversas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pacienteId").value(pacienteId))
                .andExpect(jsonPath("$[0].ultimaMensagem.conteudo").value("Já tomei"))
                .andExpect(jsonPath("$[0].naoLidas").value(1));
    }
}
