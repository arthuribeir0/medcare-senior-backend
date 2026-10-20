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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Segurança e contrato HTTP de ponta a ponta: contexto completo (filtros do Spring Security, controllers, handler de
 * erros), PostgreSQL real e tokens gerados pelo JwtTokenProvider. Cada teste roda numa transação desfeita no final
 * (o MockMvc executa na mesma thread). Jobs e FCM desligados, como no SeniorApplicationTests.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class SegurancaApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private LocalDateTime agora;

    // Cuidador A (com um idoso, um medicamento e uma dose no horário de agora: sempre no dia de hoje e dentro da
    // janela de confirmação) e cuidador B (idem)
    private Long cuidadorA;
    private Long idosoA;
    private Long medicamentoA;
    private Long doseA;
    private Long cuidadorB;
    private Long idosoB;
    private Long medicamentoB;
    private Long doseB;

    @BeforeEach
    void criarDados() {
        agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        cuidadorA = cuidador("Ana Souza", "11999999999");
        idosoA = idoso(cuidadorA, "José Souza");
        medicamentoA = medicamento(idosoA);
        doseA = dose(tratamento(medicamentoA), agora);
        cuidadorB = cuidador("Bruno Lima", null);
        idosoB = idoso(cuidadorB, "Helena Lima");
        medicamentoB = medicamento(idosoB);
        doseB = dose(tratamento(medicamentoB), agora);
    }

    // ---- autenticação e papéis ------------------------------------------------------------------------------------

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get("/api/idosos")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/idosos/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/doses/" + doseA + "/confirmar")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeCuidadorEmRotaDeIdosoResponde403() throws Exception {
        mvc.perform(comoCuidador(get("/api/idosos/me"), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoCuidador(get("/api/idosos/me/doses"), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoCuidador(put("/api/doses/" + doseA + "/confirmar"), cuidadorA)).andExpect(status().isForbidden());
    }

    @Test
    void tokenDeIdosoEmRotaDeCuidadorResponde403() throws Exception {
        mvc.perform(comoIdoso(get("/api/idosos"), idosoA)).andExpect(status().isForbidden());
        mvc.perform(comoIdoso(get("/api/idosos/" + idosoA), idosoA)).andExpect(status().isForbidden());
        mvc.perform(comoIdoso(get("/api/medicamentos/" + medicamentoA), idosoA)).andExpect(status().isForbidden());
        mvc.perform(comoIdoso(get("/api/cuidadores/me"), idosoA)).andExpect(status().isForbidden());
    }

    @Test
    void rotaForaDaListaEhNegadaMesmoComToken() throws Exception {
        mvc.perform(comoCuidador(get("/api/rota-que-nao-existe"), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoIdoso(get("/api/doses/" + doseA), idosoA)).andExpect(status().isForbidden());
        mvc.perform(comoCuidador(get("/api/cuidadores"), cuidadorA)).andExpect(status().isForbidden());
    }

    // ---- escopo do cuidador (404, nunca 403) ----------------------------------------------------------------------

    @Test
    void cuidadorANaoVeIdosoMedicamentoNemDosesDoCuidadorB() throws Exception {
        mvc.perform(comoCuidador(get("/api/idosos/" + idosoB), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoCuidador(get("/api/medicamentos/" + medicamentoB), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoCuidador(get("/api/idosos/" + idosoB + "/doses"), cuidadorA))
                .andExpect(status().isNotFound());

        mvc.perform(comoCuidador(get("/api/idosos"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(idosoA));
        mvc.perform(comoCuidador(get("/api/idosos/" + idosoA + "/doses"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doseA));
    }

    // ---- rotas do idoso -------------------------------------------------------------------------------------------

    @Test
    void idosoVeOsPropriosDadosEAsDosesDeHoje() throws Exception {
        // "me" casa com o mapeamento literal, não com /api/idosos/{id}
        mvc.perform(comoIdoso(get("/api/idosos/me"), idosoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idosoA))
                .andExpect(jsonPath("$.nome").value("José Souza"))
                .andExpect(jsonPath("$.cuidadorNome").value("Ana Souza"));

        mvc.perform(comoIdoso(get("/api/idosos/me/doses"), idosoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(doseA))
                .andExpect(jsonPath("$[0].idosoId").value(idosoA))
                .andExpect(jsonPath("$[0].medicamentoNome").value("Losartana"))
                .andExpect(jsonPath("$[0].tipo").value("COMPRIMIDO"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0].statusExibicao").value("PENDENTE"))
                .andExpect(jsonPath("$[0].confirmabilidade").value("OK"))
                .andExpect(jsonPath("$[0].toleranciaMinutos").value(30))
                .andExpect(jsonPath("$[0].janela.abreEm", notNullValue()))
                .andExpect(jsonPath("$[0].janela.avisoEm", notNullValue()))
                .andExpect(jsonPath("$[0].janela.encerraEm", notNullValue()));

        mvc.perform(comoIdoso(get("/api/idosos/me/doses").param("data", "2000-01-01"), idosoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dataInvalidaResponde400Validacao() throws Exception {
        mvc.perform(comoIdoso(get("/api/idosos/me/doses").param("data", "31/12/2026"), idosoA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("data"));
    }

    @Test
    void tokenDeIdosoDeContaExcluidaResponde401SessaoInvalida() throws Exception {
        Long idosoExcluido = idoso(cuidadorA, "Idoso excluído");
        jdbc.update("DELETE FROM idoso WHERE id = ?", idosoExcluido);

        mvc.perform(comoIdoso(get("/api/idosos/me"), idosoExcluido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        mvc.perform(comoIdoso(get("/api/idosos/me/doses"), idosoExcluido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        mvc.perform(comoIdoso(put("/api/doses/" + doseA + "/confirmar"), idosoExcluido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
    }

    // ---- confirmação de dose --------------------------------------------------------------------------------------

    @Test
    void tokenDeCuidadorDeContaExcluidaResponde401SessaoInvalidaEmTodasAsRotas() throws Exception {
        Long cuidadorExcluido = cuidador("Conta excluída", null);
        jdbc.update("DELETE FROM cuidador WHERE id = ?", cuidadorExcluido);

        List<MockHttpServletRequestBuilder> rotas = List.of(
                get("/api/cuidadores/me"),
                delete("/api/cuidadores/me"),
                put("/api/cuidadores/me/fcm-token").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"fcm-aparelho\"}"),
                delete("/api/cuidadores/me/fcm-token"),
                get("/api/idosos"),
                post("/api/idosos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Maria\", \"dataNascimento\": \"1945-03-12\"}"),
                // Recurso de outro cuidador: a sessão inválida vem antes do 404
                get("/api/idosos/" + idosoA),
                get("/api/idosos/" + idosoA + "/doses"),
                post("/api/idosos/" + idosoA + "/gerar-codigo"),
                get("/api/medicamentos"),
                get("/api/medicamentos/" + medicamentoA));
        for (MockHttpServletRequestBuilder rota : rotas) {
            mvc.perform(comoCuidador(rota, cuidadorExcluido))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"))
                    .andExpect(jsonPath("$.mensagem").value("Sessão inválida. Entre na sua conta de novo."));
        }

        // Cuidador que existe continua recebendo 404 para o que não é dele
        mvc.perform(comoCuidador(get("/api/idosos/" + idosoA), cuidadorB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void confirmarDoseNoPrazoDevolveTomadaEDuasVezesResponde409() throws Exception {
        mvc.perform(comoIdoso(put("/api/doses/" + doseA + "/confirmar"), idosoA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doseA))
                .andExpect(jsonPath("$.status").value("TOMADA"))
                .andExpect(jsonPath("$.statusExibicao").value("TOMADA"))
                .andExpect(jsonPath("$.horaConfirmacao", notNullValue()))
                .andExpect(jsonPath("$.confirmabilidade").value("JA_REGISTRADA"));

        mvc.perform(comoIdoso(put("/api/doses/" + doseA + "/confirmar"), idosoA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_JA_REGISTRADA"));
    }

    @Test
    void confirmarDoseDeOutroIdosoResponde404() throws Exception {
        mvc.perform(comoIdoso(put("/api/doses/" + doseB + "/confirmar"), idosoA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoIdoso(put("/api/doses/" + Long.MAX_VALUE + "/confirmar"), idosoA))
                .andExpect(status().isNotFound());
    }

    // ---- cuidador -------------------------------------------------------------------------------------------------

    @Test
    void cadastroDeCuidadorContinuaPublico() throws Exception {
        String corpo = """
                {"nome": "Carla Dias", "email": "%s", "senha": "SenhaForte1", "telefone": "11988887777",
                 "consentimentoLgpd": true}
                """.formatted("publico-" + UUID.randomUUID() + "@teste.com");

        mvc.perform(post("/api/cuidadores").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.telefone").value("11988887777"));
    }

    @Test
    void cuidadorVeOsPropriosDados() throws Exception {
        mvc.perform(comoCuidador(get("/api/cuidadores/me"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cuidadorA))
                .andExpect(jsonPath("$.nome").value("Ana Souza"))
                .andExpect(jsonPath("$.email", notNullValue()))
                .andExpect(jsonPath("$.telefone").value("11999999999"));
    }

    @Test
    void apagarTokenDeDispositivoEhIdempotente() throws Exception {
        jdbc.update("UPDATE cuidador SET fcm_token = 'fcm-aparelho' WHERE id = ?", cuidadorA);

        mvc.perform(comoCuidador(delete("/api/cuidadores/me/fcm-token"), cuidadorA)).andExpect(status().isNoContent());
        mvc.perform(comoCuidador(delete("/api/cuidadores/me/fcm-token"), cuidadorA)).andExpect(status().isNoContent());

        assertThat(
                jdbc.queryForObject("SELECT fcm_token FROM cuidador WHERE id = ?", String.class, cuidadorA)).isNull();
        mvc.perform(comoIdoso(delete("/api/cuidadores/me/fcm-token"), idosoA)).andExpect(status().isForbidden());
    }

    // ---- formato do ErroResponse ----------------------------------------------------------------------------------

    @Test
    void erroResponseTemCodigoEFormatoPadrao() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"naoexiste-" + UUID.randomUUID() + "@teste.com\", \"senha\": \"Errada123\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.erro").value("Unauthorized"))
                .andExpect(jsonPath("$.codigo").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.mensagem").value("E-mail ou senha inválidos"))
                .andExpect(jsonPath("$.campos", hasSize(0)));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\": \"invalido\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos", hasSize(2)));
    }

    // ---- montagem -------------------------------------------------------------------------------------------------

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder requisicao, Long cuidadorId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR));
    }

    private MockHttpServletRequestBuilder comoIdoso(MockHttpServletRequestBuilder requisicao, Long idosoId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoId, Role.IDOSO));
    }

    private Long cuidador(String nome, String telefone) {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, telefone, data_cadastro, consentimento_lgpd, "
                        + "data_consentimento) VALUES (?, ?, 'hash', ?, ?, true, ?) RETURNING id", Long.class,
                nome, "mockmvc-" + UUID.randomUUID() + "@teste.com", telefone, agora, agora);
    }

    private Long idoso(Long cuidadorId, String nome) {
        return jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidadorId);
    }

    private Long medicamento(Long idosoId) {
        return jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, idosoId);
    }

    private Long tratamento(Long medicamentoId) {
        return jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, agora.toLocalDate().minusDays(1));
    }

    private Long dose(Long tratamentoId, LocalDateTime horario) {
        return jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) "
                + "VALUES (?, ?, 'PENDENTE') RETURNING id", Long.class, tratamentoId, horario);
    }
}
