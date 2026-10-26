package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${jwt.secret}")
    private String segredoJwt;

    private LocalDateTime agora;

    // Cuidador A (com um paciente, um medicamento e uma dose no horário de agora: sempre no dia de hoje e dentro da
    // janela de confirmação) e cuidador B (idem)
    private Long cuidadorA;
    private Long pacienteA;
    private Long medicamentoA;
    private Long doseA;
    private Long cuidadorB;
    private Long pacienteB;
    private Long medicamentoB;
    private Long doseB;

    @BeforeEach
    void criarDados() {
        agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        cuidadorA = cuidador("Ana Souza", "11999999999");
        pacienteA = paciente(cuidadorA, "José Souza");
        medicamentoA = medicamento(pacienteA);
        doseA = dose(tratamento(medicamentoA), agora);
        cuidadorB = cuidador("Bruno Lima", null);
        pacienteB = paciente(cuidadorB, "Helena Lima");
        medicamentoB = medicamento(pacienteB);
        doseB = dose(tratamento(medicamentoB), agora);
    }

    // ---- autenticação e papéis ------------------------------------------------------------------------------------

    @Test
    void semTokenResponde401() throws Exception {
        mvc.perform(get("/api/pacientes")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/pacientes/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/doses/" + doseA + "/confirmar")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDeCuidadorEmRotaDePacienteResponde403() throws Exception {
        mvc.perform(comoCuidador(get("/api/pacientes/me"), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoCuidador(get("/api/pacientes/me/doses"), cuidadorA)).andExpect(status().isForbidden());
    }

    @Test
    void tokenSemPapelOuComPapelDesconhecidoResponde401() throws Exception {
        // Assinado com a chave certa, mas sem o papel (ou com um papel que não existe): não autentica
        javax.crypto.SecretKey chave = io.jsonwebtoken.security.Keys.hmacShaKeyFor(segredoJwt.getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        java.util.Date agoraData = new java.util.Date();
        java.util.Date expira = new java.util.Date(agoraData.getTime() + 60_000);
        String semPapel = io.jsonwebtoken.Jwts.builder().subject(String.valueOf(cuidadorA)).issuedAt(agoraData)
                .expiration(expira).signWith(chave).compact();
        String papelDesconhecido = io.jsonwebtoken.Jwts.builder().subject(String.valueOf(cuidadorA)).claim("role", "ADMIN")
                .issuedAt(agoraData).expiration(expira).signWith(chave).compact();

        for (String token : List.of(semPapel, papelDesconhecido)) {
            mvc.perform(put("/api/doses/" + doseA + "/confirmar").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseA)).isEqualTo("PENDENTE");
    }

    @Test
    void tokenEmitidoComONomeAntigoDoPapelContinuaValendoComoPaciente() throws Exception {
        // Aparelhos pareados antes da troca de nome guardam um token com role IDOSO (válido por 180 dias)
        javax.crypto.SecretKey chave = io.jsonwebtoken.security.Keys.hmacShaKeyFor(segredoJwt.getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        java.util.Date agoraData = new java.util.Date();
        String tokenAntigo = io.jsonwebtoken.Jwts.builder().subject(String.valueOf(pacienteA)).claim("role", "IDOSO")
                .issuedAt(agoraData).expiration(new java.util.Date(agoraData.getTime() + 60_000)).signWith(chave).compact();

        mvc.perform(get("/api/pacientes/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAntigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pacienteA));
        mvc.perform(get("/api/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAntigo))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenDePacienteEmRotaDeCuidadorResponde403() throws Exception {
        mvc.perform(comoPaciente(get("/api/pacientes"), pacienteA)).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(get("/api/pacientes/" + pacienteA), pacienteA)).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(get("/api/medicamentos/" + medicamentoA), pacienteA)).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(get("/api/cuidadores/me"), pacienteA)).andExpect(status().isForbidden());
    }

    @Test
    void rotaForaDaListaEhNegadaMesmoComToken() throws Exception {
        mvc.perform(comoCuidador(get("/api/rota-que-nao-existe"), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(get("/api/doses/" + doseA), pacienteA)).andExpect(status().isForbidden());
        // A confirmação só existe como PUT: outro método na mesma rota cai no bloqueio padrão, para os dois papéis
        mvc.perform(comoCuidador(post("/api/doses/" + doseA + "/confirmar"), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(post("/api/doses/" + doseA + "/confirmar"), pacienteA)).andExpect(status().isForbidden());
        mvc.perform(comoCuidador(put("/api/doses/" + doseA), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(comoCuidador(get("/api/cuidadores"), cuidadorA)).andExpect(status().isForbidden());
    }

    // ---- escopo do cuidador (404, nunca 403) ----------------------------------------------------------------------

    @Test
    void cuidadorANaoVePacienteMedicamentoNemDosesDoCuidadorB() throws Exception {
        mvc.perform(comoCuidador(get("/api/pacientes/" + pacienteB), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoCuidador(get("/api/medicamentos/" + medicamentoB), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoCuidador(get("/api/pacientes/" + pacienteB + "/doses"), cuidadorA))
                .andExpect(status().isNotFound());

        mvc.perform(comoCuidador(get("/api/pacientes"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pacienteA));
        mvc.perform(comoCuidador(get("/api/pacientes/" + pacienteA + "/doses"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(doseA));
    }

    // ---- rotas do paciente -------------------------------------------------------------------------------------------

    @Test
    void pacienteVeOsPropriosDadosEAsDosesDeHoje() throws Exception {
        // "me" casa com o mapeamento literal, não com /api/pacientes/{id}
        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pacienteA))
                .andExpect(jsonPath("$.nome").value("José Souza"))
                .andExpect(jsonPath("$.cuidadorNome").value("Ana Souza"));

        mvc.perform(comoPaciente(get("/api/pacientes/me/doses"), pacienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(doseA))
                .andExpect(jsonPath("$[0].pacienteId").value(pacienteA))
                .andExpect(jsonPath("$[0].medicamentoNome").value("Losartana"))
                .andExpect(jsonPath("$[0].tipo").value("COMPRIMIDO"))
                .andExpect(jsonPath("$[0].status").value("PENDENTE"))
                .andExpect(jsonPath("$[0].statusExibicao").value("PENDENTE"))
                .andExpect(jsonPath("$[0].confirmabilidade").value("OK"))
                .andExpect(jsonPath("$[0].toleranciaMinutos").value(30))
                .andExpect(jsonPath("$[0].janela.abreEm", notNullValue()))
                .andExpect(jsonPath("$[0].janela.avisoEm", notNullValue()))
                .andExpect(jsonPath("$[0].janela.encerraEm", notNullValue()))
                .andExpect(jsonPath("$[0].limiteConfirmacao", notNullValue()));

        mvc.perform(comoPaciente(get("/api/pacientes/me/doses").param("data", "2000-01-01"), pacienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void dataInvalidaResponde400Validacao() throws Exception {
        mvc.perform(comoPaciente(get("/api/pacientes/me/doses").param("data", "31/12/2026"), pacienteA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("data"));
    }

    @Test
    void tokenDePacienteDeContaExcluidaResponde401SessaoInvalida() throws Exception {
        Long pacienteExcluido = paciente(cuidadorA, "Paciente excluído");
        jdbc.update("DELETE FROM paciente WHERE id = ?", pacienteExcluido);

        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteExcluido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        mvc.perform(comoPaciente(get("/api/pacientes/me/doses"), pacienteExcluido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        mvc.perform(comoPaciente(put("/api/doses/" + doseA + "/confirmar"), pacienteExcluido))
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
                get("/api/pacientes"),
                post("/api/pacientes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Maria\", \"dataNascimento\": \"1945-03-12\"}"),
                // Recurso de outro cuidador: a sessão inválida vem antes do 404
                get("/api/pacientes/" + pacienteA),
                get("/api/pacientes/" + pacienteA + "/doses"),
                post("/api/pacientes/" + pacienteA + "/gerar-codigo"),
                get("/api/medicamentos"),
                get("/api/medicamentos/" + medicamentoA),
                put("/api/doses/" + doseA + "/confirmar"));
        for (MockHttpServletRequestBuilder rota : rotas) {
            mvc.perform(comoCuidador(rota, cuidadorExcluido))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"))
                    .andExpect(jsonPath("$.mensagem").value("Sessão inválida. Entre na sua conta de novo."));
        }

        // Cuidador que existe continua recebendo 404 para o que não é dele
        mvc.perform(comoCuidador(get("/api/pacientes/" + pacienteA), cuidadorB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void confirmarDoseNoPrazoDevolveConfirmadaNoHorarioEDuasVezesResponde409() throws Exception {
        mvc.perform(comoPaciente(put("/api/doses/" + doseA + "/confirmar"), pacienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(doseA))
                .andExpect(jsonPath("$.status").value("CONFIRMADA_NO_HORARIO"))
                .andExpect(jsonPath("$.statusExibicao").value("CONFIRMADA_NO_HORARIO"))
                .andExpect(jsonPath("$.horaConfirmacao", notNullValue()))
                .andExpect(jsonPath("$.confirmabilidade").value("JA_REGISTRADA"));

        mvc.perform(comoPaciente(put("/api/doses/" + doseA + "/confirmar"), pacienteA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_JA_REGISTRADA"));
    }

    @Test
    void cuidadorConfirmaDoseDoProprioPacienteENaoADoPacienteDeOutroCuidador() throws Exception {
        mvc.perform(comoCuidador(put("/api/doses/" + doseB + "/confirmar"), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensagem").value("Dose não encontrada"));
        assertThat(jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseB)).isEqualTo("PENDENTE");

        mvc.perform(comoCuidador(put("/api/doses/" + doseA + "/confirmar"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confirmadoPor").value("CUIDADOR"));
    }

    @Test
    void pacienteNaoConfirmaDoseDeOutroPacienteNemPeloIdDoCuidador() throws Exception {
        // O ID vem do token: um paciente com o mesmo número de um cuidador continua sendo só paciente
        mvc.perform(comoPaciente(put("/api/doses/" + doseB + "/confirmar"), pacienteA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Dose não encontrada"));
        assertThat(jdbc.queryForObject("SELECT confirmado_por FROM dose WHERE id = ?", String.class, doseB)).isNull();
    }

    @Test
    void confirmarDoseDeOutroPacienteResponde404() throws Exception {
        mvc.perform(comoPaciente(put("/api/doses/" + doseB + "/confirmar"), pacienteA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoPaciente(put("/api/doses/" + Long.MAX_VALUE + "/confirmar"), pacienteA))
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
        mvc.perform(comoPaciente(delete("/api/cuidadores/me/fcm-token"), pacienteA)).andExpect(status().isForbidden());
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

    private MockHttpServletRequestBuilder comoPaciente(MockHttpServletRequestBuilder requisicao, Long pacienteId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(pacienteId, Role.PACIENTE));
    }

    private Long cuidador(String nome, String telefone) {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, telefone, data_cadastro, consentimento_lgpd, "
                        + "data_consentimento) VALUES (?, ?, 'hash', ?, ?, true, ?) RETURNING id", Long.class,
                nome, "mockmvc-" + UUID.randomUUID() + "@teste.com", telefone, agora, agora);
    }

    private Long paciente(Long cuidadorId, String nome) {
        return jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidadorId);
    }

    private Long medicamento(Long pacienteId) {
        return jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, pacienteId);
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
