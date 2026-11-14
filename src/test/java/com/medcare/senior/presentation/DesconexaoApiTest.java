package com.medcare.senior.presentation;

import com.medcare.senior.application.usecase.ExpirarPedidosDesconexaoUseCase;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saída da conta do paciente com autorização do cuidador, de ponta a ponta: segurança, controllers, use cases e
 * PostgreSQL real. Cada teste roda numa transação desfeita no final. Jobs e FCM desligados (o aviso vai só ao log).
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class DesconexaoApiTest {

    private static final String PEDIDO_DO_PACIENTE = "/api/pacientes/me/pedido-desconexao";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private Clock clock;

    @Autowired
    private ExpirarPedidosDesconexaoUseCase expirarPedidos;

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

    // ---- paciente pede, consulta e cancela ---------------------------------------------------------------------------

    @Test
    void pacientePedeEPedirDeNovoDevolveOMesmoPedido() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoPaciente(post(PEDIDO_DO_PACIENTE), pacienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedido))
                .andExpect(jsonPath("$.status").value("PENDENTE"));
        mvc.perform(comoPaciente(get(PEDIDO_DO_PACIENTE), pacienteA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pedido))
                .andExpect(jsonPath("$.pacienteId").value(pacienteA))
                .andExpect(jsonPath("$.decididoEm").doesNotExist());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedido_desconexao WHERE paciente_id = ?", Long.class, pacienteA))
                .isEqualTo(1L);
    }

    @Test
    void pedidoValeVinteEQuatroHoras() throws Exception {
        long pedido = pedir(pacienteA);

        LocalDateTime criado = jdbc.queryForObject("SELECT criado_em FROM pedido_desconexao WHERE id = ?", LocalDateTime.class, pedido);
        LocalDateTime expira = jdbc.queryForObject("SELECT expira_em FROM pedido_desconexao WHERE id = ?", LocalDateTime.class, pedido);
        assertThat(expira).isEqualTo(criado.plusHours(24));
    }

    @Test
    void semPedidoAConsultaResponde404() throws Exception {
        mvc.perform(comoPaciente(get(PEDIDO_DO_PACIENTE), pacienteA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void pacienteCancelaEOCuidadorNaoVeMaisOPedido() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoPaciente(delete(PEDIDO_DO_PACIENTE), pacienteA)).andExpect(status().isNoContent());
        mvc.perform(comoPaciente(delete(PEDIDO_DO_PACIENTE), pacienteA)).andExpect(status().isNoContent());

        mvc.perform(comoPaciente(get(PEDIDO_DO_PACIENTE), pacienteA)).andExpect(jsonPath("$.status").value("CANCELADO"));
        mvc.perform(comoCuidador(get("/api/pacientes/pedidos-desconexao"), cuidadorA)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "autorizar")), cuidadorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_ENCERRADO"));
    }

    // ---- cuidador vê, autoriza, recusa ---------------------------------------------------------------------------------

    @Test
    void cuidadorVeSoOsPedidosDosPacientesDele() throws Exception {
        long pedido = pedir(pacienteA);
        pedir(pacienteB);

        mvc.perform(comoCuidador(get("/api/pacientes/pedidos-desconexao"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(pedido))
                .andExpect(jsonPath("$[0].pacienteId").value(pacienteA))
                .andExpect(jsonPath("$[0].pacienteNome").value("José Souza"));
    }

    @Test
    void autorizarDesconectaOAparelhoMasMantemOVinculo() throws Exception {
        long pedido = pedir(pacienteA);
        String codigoAntigo = gerarCodigo(pacienteA);
        String tokenDoAparelho = tokens.generateToken(pacienteA, Role.PACIENTE);

        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "autorizar")), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTORIZADO"))
                .andExpect(jsonPath("$.decididoEm").exists());

        // O aparelho cai em qualquer rota, com SESSAO_INVALIDA (o app volta à tela inicial)
        for (MockHttpServletRequestBuilder rota : new MockHttpServletRequestBuilder[]{
                get("/api/pacientes/me"), get("/api/pacientes/me/doses"), get(PEDIDO_DO_PACIENTE)}) {
            mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoAparelho))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        }
        // Vínculo, dados e decisão preservados; o código gerado antes não serve mais
        mvc.perform(comoCuidador(get("/api/pacientes/" + pacienteA), cuidadorA)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT decidido_por_cuidador_id FROM pedido_desconexao WHERE id = ?", Long.class, pedido))
                .isEqualTo(cuidadorA);
        validarCodigo(codigoAntigo).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_INVALIDO"));
        // Nenhum outro paciente foi afetado
        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteB)).andExpect(status().isOk());
    }

    @Test
    void depoisDeAutorizadoUmCodigoNovoReconectaOAparelho() throws Exception {
        long pedido = pedir(pacienteA);
        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "autorizar")), cuidadorA)).andExpect(status().isOk());
        // O marcador vai para o segundo seguinte à desconexão (o "emitido em" do JWT é em segundos)
        Thread.sleep(1_100);

        String resposta = validarCodigo(gerarCodigo(pacienteA)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String tokenNovo = json.readTree(resposta).path("token").asString();

        mvc.perform(get("/api/pacientes/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNovo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pacienteA));
    }

    @Test
    void recusarMantemOPacienteConectado() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "recusar")), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));

        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteA)).andExpect(status().isOk());
        mvc.perform(comoPaciente(get(PEDIDO_DO_PACIENTE), pacienteA)).andExpect(jsonPath("$.status").value("RECUSADO"));
        // Depois de recusado, o paciente pode pedir de novo
        assertThat(pedir(pacienteA)).isNotEqualTo(pedido);
    }

    @Test
    void decidirDuasVezesNaoDuplicaEDecidirOContrarioResponde409() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "recusar")), cuidadorA)).andExpect(status().isOk());
        String decididoEm = jdbc.queryForObject("SELECT decidido_em::text FROM pedido_desconexao WHERE id = ?", String.class, pedido);
        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "recusar")), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECUSADO"));
        assertThat(jdbc.queryForObject("SELECT decidido_em::text FROM pedido_desconexao WHERE id = ?", String.class, pedido))
                .isEqualTo(decididoEm);

        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "autorizar")), cuidadorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_ENCERRADO"));
        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteA)).andExpect(status().isOk());
    }

    @Test
    void pedidoVencidoSaiExpiradoNaoPodeSerDecididoEOPacientePedeDeNovo() throws Exception {
        long pedido = pedir(pacienteA);
        // O job ainda não passou: o banco continua PENDENTE, mas o prazo já venceu
        jdbc.update("UPDATE pedido_desconexao SET criado_em = ?, expira_em = ? WHERE id = ?",
                agora.minusHours(25), agora.minusHours(1), pedido);

        mvc.perform(comoPaciente(get(PEDIDO_DO_PACIENTE), pacienteA)).andExpect(jsonPath("$.status").value("EXPIRADO"));
        mvc.perform(comoCuidador(get("/api/pacientes/pedidos-desconexao"), cuidadorA)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "autorizar")), cuidadorA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PEDIDO_ENCERRADO"));
        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteA)).andExpect(status().isOk());

        long novo = pedir(pacienteA);
        assertThat(novo).isNotEqualTo(pedido);
        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, pedido))
                .isEqualTo("EXPIRADO");
    }

    @Test
    void jobMarcaOsVencidosComoExpiradosNoProprioPrazo() throws Exception {
        long vencido = pedir(pacienteA);
        long valido = pedir(pacienteB);
        LocalDateTime prazo = agora.minusMinutes(1);
        jdbc.update("UPDATE pedido_desconexao SET criado_em = ?, expira_em = ? WHERE id = ?", prazo.minusHours(24), prazo, vencido);

        assertThat(expirarPedidos.executar()).isGreaterThanOrEqualTo(1);

        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, vencido))
                .isEqualTo("EXPIRADO");
        assertThat(jdbc.queryForObject("SELECT decidido_em FROM pedido_desconexao WHERE id = ?", LocalDateTime.class, vencido))
                .isEqualTo(prazo);
        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, valido))
                .isEqualTo("PENDENTE");
    }

    @Test
    void cuidadorDesconectaOAparelhoSemPedidoEUmPendenteFicaAutorizado() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoCuidador(post("/api/pacientes/" + pacienteA + "/desconectar-aparelho"), cuidadorA))
                .andExpect(status().isNoContent());

        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteA))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, pedido))
                .isEqualTo("AUTORIZADO");
        mvc.perform(comoCuidador(post("/api/pacientes/" + pacienteA + "/desconectar-aparelho"), cuidadorA))
                .andExpect(status().isNoContent());
    }

    // ---- donos e papéis ---------------------------------------------------------------------------------------------------

    @Test
    void cuidadorDeOutroPacienteRecebe404ENadaMuda() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoCuidador(put(decisao(pacienteA, pedido, "autorizar")), cuidadorB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoCuidador(post("/api/pacientes/" + pacienteA + "/desconectar-aparelho"), cuidadorB))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, pedido))
                .isEqualTo("PENDENTE");
        mvc.perform(comoPaciente(get("/api/pacientes/me"), pacienteA)).andExpect(status().isOk());
    }

    @Test
    void pedidoDeOutroPacienteNaRotaDoMeuPacienteResponde404() throws Exception {
        long pedidoDeB = pedir(pacienteB);
        Long outroPacienteDeA = paciente(cuidadorA, "Clara Souza");

        mvc.perform(comoCuidador(put(decisao(outroPacienteDeA, pedidoDeB, "autorizar")), cuidadorA))
                .andExpect(status().isNotFound());
        mvc.perform(comoCuidador(put(decisao(pacienteA, pedidoDeB, "recusar")), cuidadorA))
                .andExpect(status().isNotFound());

        assertThat(jdbc.queryForObject("SELECT status FROM pedido_desconexao WHERE id = ?", String.class, pedidoDeB))
                .isEqualTo("PENDENTE");
    }

    @Test
    void pacienteNaoAutorizaNemDecideEOCuidadorNaoPedePeloPaciente() throws Exception {
        long pedido = pedir(pacienteA);

        mvc.perform(comoPaciente(put(decisao(pacienteA, pedido, "autorizar")), pacienteA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mvc.perform(comoPaciente(get("/api/pacientes/pedidos-desconexao"), pacienteA)).andExpect(status().isForbidden());
        mvc.perform(comoPaciente(post("/api/pacientes/" + pacienteA + "/desconectar-aparelho"), pacienteA))
                .andExpect(status().isForbidden());
        mvc.perform(comoCuidador(post(PEDIDO_DO_PACIENTE), cuidadorA)).andExpect(status().isForbidden());
        mvc.perform(post(PEDIDO_DO_PACIENTE)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenDePacienteQueNaoExisteMaisTemSessaoInvalida() throws Exception {
        mvc.perform(comoPaciente(post(PEDIDO_DO_PACIENTE), Long.MAX_VALUE))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
    }

    // ---- apoio -----------------------------------------------------------------------------------------------------------

    private long pedir(Long pacienteId) throws Exception {
        String resposta = mvc.perform(comoPaciente(post(PEDIDO_DO_PACIENTE), pacienteId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andReturn().getResponse().getContentAsString();
        JsonNode pedido = json.readTree(resposta);
        return pedido.path("id").asLong();
    }

    private String gerarCodigo(Long pacienteId) throws Exception {
        String resposta = mvc.perform(comoCuidador(post("/api/pacientes/" + pacienteId + "/gerar-codigo"), cuidadorA))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resposta).path("codigo").asString();
    }

    private org.springframework.test.web.servlet.ResultActions validarCodigo(String codigo) throws Exception {
        return mvc.perform(post("/api/vinculo/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"codigo\":\"" + codigo + "\"}")
                .with(requisicao -> {
                    // IP próprio: as falhas deste teste não bloqueiam o pareamento dos outros
                    requisicao.setRemoteAddr("192.0.2." + (1 + Math.floorMod(codigo.hashCode(), 200)));
                    return requisicao;
                }));
    }

    private static String decisao(Long pacienteId, long pedidoId, String acao) {
        return "/api/pacientes/" + pacienteId + "/pedidos-desconexao/" + pedidoId + "/" + acao;
    }

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder requisicao, Long cuidadorId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR));
    }

    private MockHttpServletRequestBuilder comoPaciente(MockHttpServletRequestBuilder requisicao, Long pacienteId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(pacienteId, Role.PACIENTE));
    }

    private Long cuidador(String nome) {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                        + "data_consentimento) VALUES (?, ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                nome, "desconexao-" + UUID.randomUUID() + "@teste.com", agora, agora);
    }

    private Long paciente(Long cuidadorId, String nome) {
        return jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome, LocalDate.of(1945, 3, 12), cuidadorId);
    }
}
