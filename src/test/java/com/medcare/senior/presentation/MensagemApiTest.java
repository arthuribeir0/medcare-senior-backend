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
    private Long idosoId;
    private Long outroIdosoDoCuidador;
    private Long idosoDeOutroCuidador;

    @BeforeEach
    void criarDados() {
        cuidadorId = cuidador();
        idosoId = idoso(cuidadorId, "Maria");
        outroIdosoDoCuidador = idoso(cuidadorId, "João");
        idosoDeOutroCuidador = idoso(cuidador(), "Alheio");
    }

    private Long cuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador mensagens', ?, 'hash', now(), true, now()) RETURNING id",
                Long.class, "mensagens-api-" + UUID.randomUUID() + "@teste.com");
    }

    private Long idoso(Long cuidador, String nome) {
        return jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidador);
    }

    private void mensagem(Long idoso, String remetente, String conteudo, LocalDateTime quando) {
        jdbc.update("INSERT INTO mensagem (idoso_id, remetente, conteudo, criada_em) VALUES (?, ?, ?, ?)", idoso,
                remetente, conteudo, quando);
    }

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR));
    }

    private MockHttpServletRequestBuilder comoIdoso(MockHttpServletRequestBuilder r) {
        return r.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoId, Role.IDOSO));
    }

    private static MockHttpServletRequestBuilder enviar(String json) {
        return post(ROTA).contentType(MediaType.APPLICATION_JSON).content(json);
    }

    private static void erro(ResultActions resultado, int status, String codigo) throws Exception {
        resultado.andExpect(status().is(status)).andExpect(jsonPath("$.codigo").value(codigo));
    }

    // --- Envio ----------------------------------------------------------------------------------------------------

    @Test
    void cuidadorEnviaParaUmIdosoSeuEIdosoEnviaParaOCuidador() throws Exception {
        mvc.perform(comoCuidador(enviar("{\"idosoId\": " + idosoId + ", \"conteudo\": \"  Bom dia, Maria!  \"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.remetente").value("CUIDADOR"))
                .andExpect(jsonPath("$.conteudo").value("Bom dia, Maria!"))
                .andExpect(jsonPath("$.criadaEm", notNullValue()))
                .andExpect(jsonPath("$.lidaEm", nullValue()));
        // O idoso não precisa (nem pode) escolher a conversa: é sempre a dele
        mvc.perform(comoIdoso(enviar("{\"conteudo\": \"Bom dia!\"}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.remetente").value("IDOSO"));
        mvc.perform(comoIdoso(enviar("{\"idosoId\": " + idosoId + ", \"conteudo\": \"De novo\"}")))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM mensagem WHERE idoso_id = ?", Long.class, idosoId)).isEqualTo(3);
    }

    @Test
    void conteudoVazioSoEspacosAusenteOuMaiorQue500Eh400NoCampoConteudo() throws Exception {
        for (String corpo : List.of("{\"idosoId\": " + idosoId + ", \"conteudo\": \"\"}",
                "{\"idosoId\": " + idosoId + ", \"conteudo\": \"   \\n \"}",
                "{\"idosoId\": " + idosoId + "}",
                "{\"idosoId\": " + idosoId + ", \"conteudo\": \"" + "a".repeat(501) + "\"}")) {
            erro(mvc.perform(comoCuidador(enviar(corpo))), 400, "VALIDACAO");
            mvc.perform(comoCuidador(enviar(corpo))).andExpect(jsonPath("$.campos[0].campo").value("conteudo"));
        }
        erro(mvc.perform(comoIdoso(enviar("{\"conteudo\": \"\"}"))), 400, "VALIDACAO");
        // Exatamente 500 passa
        mvc.perform(comoCuidador(enviar("{\"idosoId\": " + idosoId + ", \"conteudo\": \"" + "a".repeat(500) + "\"}")))
                .andExpect(status().isCreated());
    }

    @Test
    void cuidadorSemIdosoEh400ECorpoIlegivelEhCorpoInvalido() throws Exception {
        erro(mvc.perform(comoCuidador(enviar("{\"conteudo\": \"Oi\"}"))), 400, "VALIDACAO");
        mvc.perform(comoCuidador(enviar("{\"conteudo\": \"Oi\"}"))).andExpect(jsonPath("$.campos[0].campo").value("idosoId"));
        erro(mvc.perform(comoCuidador(enviar("{nao e json"))), 400, "CORPO_INVALIDO");
    }

    // --- Acesso ---------------------------------------------------------------------------------------------------

    @Test
    void cuidadorNaoAcessaIdosoDeOutroCuidadorEmNenhumaRota() throws Exception {
        erro(mvc.perform(comoCuidador(enviar("{\"idosoId\": " + idosoDeOutroCuidador + ", \"conteudo\": \"Oi\"}"))),
                404, "RECURSO_NAO_ENCONTRADO");
        erro(mvc.perform(comoCuidador(get(ROTA).param("idosoId", idosoDeOutroCuidador.toString()))),
                404, "RECURSO_NAO_ENCONTRADO");
        erro(mvc.perform(comoCuidador(put(ROTA + "/lidas").param("idosoId", idosoDeOutroCuidador.toString()))),
                404, "RECURSO_NAO_ENCONTRADO");
        erro(mvc.perform(comoCuidador(get(ROTA).param("idosoId", "999999999"))), 404, "RECURSO_NAO_ENCONTRADO");
    }

    @Test
    void idosoInformandoOutroIdosoEhAcessoNegadoEmTodasAsRotas() throws Exception {
        String outro = outroIdosoDoCuidador.toString();
        erro(mvc.perform(comoIdoso(enviar("{\"idosoId\": " + outro + ", \"conteudo\": \"Oi\"}"))), 403, "ACESSO_NEGADO");
        erro(mvc.perform(comoIdoso(get(ROTA).param("idosoId", outro))), 403, "ACESSO_NEGADO");
        erro(mvc.perform(comoIdoso(put(ROTA + "/lidas").param("idosoId", outro))), 403, "ACESSO_NEGADO");
    }

    @Test
    void semTokenEh401EmTodasAsRotas() throws Exception {
        for (MockHttpServletRequestBuilder rota : rotas()) {
            mvc.perform(rota).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void tokenDeIdosoOuCuidadorExcluidoEh401SessaoInvalidaEmTodasAsRotas() throws Exception {
        Long idosoExcluido = idoso(cuidadorId, "Excluído");
        jdbc.update("DELETE FROM idoso WHERE id = ?", idosoExcluido);
        Long cuidadorExcluido = cuidador();
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorExcluido);

        for (MockHttpServletRequestBuilder rota : rotas()) {
            erro(mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoExcluido, Role.IDOSO))),
                    401, "SESSAO_INVALIDA");
        }
        for (MockHttpServletRequestBuilder rota : rotas()) {
            erro(mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorExcluido,
                    Role.CUIDADOR))), 401, "SESSAO_INVALIDA");
        }
    }

    private List<MockHttpServletRequestBuilder> rotas() {
        return List.of(
                enviar("{\"idosoId\": " + idosoId + ", \"conteudo\": \"Oi\"}"),
                get(ROTA).param("idosoId", idosoId.toString()),
                get(ROTA + "/conversas"),
                put(ROTA + "/lidas").param("idosoId", idosoId.toString()),
                get(ROTA + "/nao-lidas"));
    }

    @Test
    void outrosMetodosERotasDeMensagensSaoNegados() throws Exception {
        mvc.perform(comoCuidador(delete(ROTA)))
                .andExpect(status().isForbidden());
        mvc.perform(comoIdoso(get(ROTA + "/qualquer"))).andExpect(status().isForbidden());
    }

    // --- Conversa, lidas e contador ----------------------------------------------------------------------------------

    @Test
    void conversaPaginadaDaMaisRecenteNosDoisPapeis() throws Exception {
        for (int i = 1; i <= 25; i++) {
            mensagem(idosoId, i % 2 == 0 ? "IDOSO" : "CUIDADOR", "m" + i, T0.plusMinutes(i));
        }
        mensagem(outroIdosoDoCuidador, "CUIDADOR", "outra conversa", T0.plusDays(1));

        mvc.perform(comoCuidador(get(ROTA).param("idosoId", idosoId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(20)))
                .andExpect(jsonPath("$.conteudo[0].conteudo").value("m25"))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.totalElementos").value(25))
                .andExpect(jsonPath("$.totalPaginas").value(2));
        mvc.perform(comoIdoso(get(ROTA).param("page", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo[*].conteudo", contains("m5", "m4", "m3", "m2", "m1")));
        mvc.perform(comoIdoso(get(ROTA).param("size", "100")))
                .andExpect(jsonPath("$.conteudo", hasSize(25)));
        for (List<String> invalido : List.of(List.of("size", "101"), List.of("size", "0"), List.of("page", "-1"))) {
            erro(mvc.perform(comoIdoso(get(ROTA).param(invalido.get(0), invalido.get(1)))), 400, "VALIDACAO");
        }
        erro(mvc.perform(comoCuidador(get(ROTA))), 400, "VALIDACAO");
    }

    @Test
    void marcarComoLidasSoMudaAsDoOutroLadoEEhIdempotente() throws Exception {
        mensagem(idosoId, "IDOSO", "a", T0);
        mensagem(idosoId, "IDOSO", "b", T0.plusMinutes(1));
        mensagem(idosoId, "CUIDADOR", "c", T0.plusMinutes(2));

        mvc.perform(comoCuidador(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(2));
        mvc.perform(comoIdoso(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(1));

        mvc.perform(comoCuidador(put(ROTA + "/lidas").param("idosoId", idosoId.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.marcadas").value(2));
        mvc.perform(comoCuidador(put(ROTA + "/lidas").param("idosoId", idosoId.toString())))
                .andExpect(jsonPath("$.marcadas").value(0));

        mvc.perform(comoCuidador(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(0));
        // A do cuidador continua não lida para o idoso
        mvc.perform(comoIdoso(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(1));
        mvc.perform(comoIdoso(get(ROTA)))
                .andExpect(jsonPath("$.conteudo[0].remetente").value("CUIDADOR"))
                .andExpect(jsonPath("$.conteudo[0].lidaEm", nullValue()))
                .andExpect(jsonPath("$.conteudo[1].lidaEm", notNullValue()));

        mvc.perform(comoIdoso(put(ROTA + "/lidas"))).andExpect(jsonPath("$.marcadas").value(1));
        mvc.perform(comoIdoso(get(ROTA + "/nao-lidas"))).andExpect(jsonPath("$.naoLidas").value(0));
    }

    @Test
    void conversasDoCuidadorEDoIdosoNoMesmoFormato() throws Exception {
        mensagem(idosoId, "IDOSO", "Já tomei", T0.plusMinutes(5));
        mensagem(idosoId, "CUIDADOR", "Bom dia", T0);
        mensagem(idosoDeOutroCuidador, "IDOSO", "não aparece", T0.plusDays(1));

        mvc.perform(comoCuidador(get(ROTA + "/conversas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].idosoId").value(idosoId))
                .andExpect(jsonPath("$[0].idosoNome").value("Maria"))
                .andExpect(jsonPath("$[0].ultimaMensagem.conteudo").value("Já tomei"))
                .andExpect(jsonPath("$[0].ultimaMensagem.remetente").value("IDOSO"))
                .andExpect(jsonPath("$[0].ultimaMensagem.criadaEm").value(T0.plusMinutes(5) + ":00"))
                .andExpect(jsonPath("$[0].naoLidas").value(1))
                .andExpect(jsonPath("$[1].idosoId").value(outroIdosoDoCuidador))
                .andExpect(jsonPath("$[1].ultimaMensagem", nullValue()))
                .andExpect(jsonPath("$[1].naoLidas").value(0));
        mvc.perform(comoIdoso(get(ROTA + "/conversas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idosoId").value(idosoId))
                .andExpect(jsonPath("$[0].ultimaMensagem.conteudo").value("Já tomei"))
                .andExpect(jsonPath("$[0].naoLidas").value(1));
    }
}
