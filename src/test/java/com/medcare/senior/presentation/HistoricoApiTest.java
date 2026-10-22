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
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Histórico de doses (UC-011, RN-011) pela API, contra o PostgreSQL real: filtros, paginação, ordem, restrição aos
 * idosos do cuidador, parâmetros inválidos e papéis; e tokens inválidos nas rotas novas (tolerância, relatório,
 * histórico). Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class HistoricoApiTest {

    private static final String ROTA = "/api/historico";
    // Dias em 2000: nenhum dado de desenvolvimento do banco local interfere nos filtros de período
    private static final LocalDate D1 = LocalDate.of(2000, 1, 10);
    private static final LocalDate D2 = D1.plusDays(1);
    private static final LocalDate D3 = D1.plusDays(2);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Value("${jwt.secret}")
    private String segredo;

    private Long cuidadorId;
    private Long idosoA;
    private Long idosoB;
    private Long losartana;
    private Long tratamentoLosartana;
    private Long tratamentoMetformina;
    private Long tratamentoDoOutro;

    @BeforeEach
    void criarDados() {
        cuidadorId = cuidador();
        idosoA = idoso(cuidadorId, "Ana");
        idosoB = idoso(cuidadorId, "Bruno");
        losartana = medicamento(idosoA, "Losartana");
        tratamentoLosartana = tratamento(losartana);
        tratamentoMetformina = tratamento(medicamento(idosoB, "Metformina"));
        tratamentoDoOutro = tratamento(medicamento(idoso(cuidador(), "Outro"), "Outro"));

        dose(tratamentoLosartana, D1.atTime(8, 0), "CONFIRMADA_NO_HORARIO");
        dose(tratamentoLosartana, D2.atTime(8, 0), "NAO_TOMADA");
        dose(tratamentoLosartana, D3.atTime(8, 0), "CONFIRMADA_COM_ATRASO");
        dose(tratamentoMetformina, D2.atTime(20, 0), "CONFIRMADA_NO_HORARIO");
        dose(tratamentoMetformina, D3.atTime(20, 0), "PENDENTE");
        dose(tratamentoDoOutro, D2.atTime(9, 0), "CONFIRMADA_NO_HORARIO");
        // Dose anonimizada (sem dono) não aparece em histórico nenhum
        jdbc.update("INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (NULL, ?, 'NAO_TOMADA')",
                D2.atTime(10, 0));
    }

    private Long cuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador histórico', ?, 'hash', now(), true, now()) RETURNING id",
                Long.class, "historico-" + UUID.randomUUID() + "@teste.com");
    }

    private Long idoso(Long cuidador, String nome) {
        return jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, nome + " " + UUID.randomUUID(), LocalDate.of(1945, 3, 12), cuidador);
    }

    private Long medicamento(Long idoso, String nome) {
        return jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) VALUES (?, '50mg', "
                + "'COMPRIMIDO', ?) RETURNING id", Long.class, nome, idoso);
    }

    private Long tratamento(Long medicamento) {
        return jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class, medicamento, D1);
    }

    private void dose(Long tratamento, LocalDateTime horario, String status) {
        boolean confirmada = "CONFIRMADA_NO_HORARIO".equals(status) || "CONFIRMADA_COM_ATRASO".equals(status);
        jdbc.update("INSERT INTO dose (tratamento_id, horario_programado, hora_confirmacao, status, confirmado_por) "
                + "VALUES (?, ?, ?, ?, ?)", tratamento, horario, confirmada ? horario.plusMinutes(5) : null, status,
                confirmada ? "IDOSO" : null);
    }

    private MockHttpServletRequestBuilder historico() {
        return historicoEntre(D1.toString(), D3.toString());
    }

    private MockHttpServletRequestBuilder historicoEntre(String de, String ate) {
        return get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR))
                .param("de", de).param("ate", ate);
    }

    @Test
    void semFiltrosTrazSoOsIdososDoCuidadorDoMaisRecenteParaOMaisAntigo() throws Exception {
        mvc.perform(historico())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.pagina").value(0))
                .andExpect(jsonPath("$.tamanho").value(20))
                .andExpect(jsonPath("$.conteudo[*].horarioProgramado", contains(
                        D3.atTime(20, 0) + ":00", D3.atTime(8, 0) + ":00", D2.atTime(20, 0) + ":00",
                        D2.atTime(8, 0) + ":00", D1.atTime(8, 0) + ":00")))
                .andExpect(jsonPath("$.conteudo[0].statusExibicao").value("NAO_TOMADA"))
                .andExpect(jsonPath("$.conteudo[0].janela.encerraEm").exists())
                .andExpect(jsonPath("$.conteudo[0].limiteConfirmacao").value(D3.atTime(21, 0) + ":00"));
    }

    @Test
    void filtraPorIdosoMedicamentoStatusEPeriodoInclusivo() throws Exception {
        mvc.perform(historico().param("idosoId", idosoB.toString()))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].medicamentoNome", contains("Metformina", "Metformina")));
        mvc.perform(historico().param("medicamentoId", losartana.toString()))
                .andExpect(jsonPath("$.totalElementos").value(3));
        mvc.perform(historico().param("status", "CONFIRMADA_NO_HORARIO"))
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.conteudo[*].status", contains("CONFIRMADA_NO_HORARIO", "CONFIRMADA_NO_HORARIO")));
        mvc.perform(historico().param("status", "CONFIRMADA_COM_ATRASO").param("idosoId", idosoA.toString()))
                .andExpect(jsonPath("$.totalElementos").value(1));
        // de e ate inclusivos: só o D2 inteiro
        mvc.perform(historicoEntre(D2.toString(), D2.toString()))
                .andExpect(jsonPath("$.totalElementos").value(2));
    }

    @Test
    void paginaComTamanhoEPaginaCalculaOTotal() throws Exception {
        mvc.perform(historico().param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(2)))
                .andExpect(jsonPath("$.pagina").value(1))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.totalElementos").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.conteudo[0].horarioProgramado").value(D2.atTime(20, 0) + ":00"));
        mvc.perform(historico().param("size", "100").param("page", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(0)));
    }

    @Test
    void periodoSemRegistrosDevolvePaginaVaziaSemErro() throws Exception {
        // RN-011: período sem registros não bloqueia a consulta
        mvc.perform(historicoEntre("1990-01-01", "1990-12-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conteudo", hasSize(0)))
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void semAteValeHojeERemedioRemovidoContinuaNoHistorico() throws Exception {
        LocalDate hoje = LocalDate.now(clock);
        dose(tratamentoLosartana, hoje.atStartOfDay(), "NAO_TOMADA");
        jdbc.update("UPDATE medicamento SET removido_em = now() WHERE id = ?", losartana);

        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR))
                        .param("idosoId", idosoA.toString()).param("de", hoje.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(1))
                .andExpect(jsonPath("$.conteudo[0].medicamentoNome").value("Losartana"));
    }

    @Test
    void parametrosInvalidosSao400ComOCampo() throws Exception {
        for (List<String> caso : List.of(
                List.of("de", D3.toString(), "de"),
                List.of("size", "101", "size"),
                List.of("size", "0", "size"),
                List.of("page", "-1", "page"),
                List.of("status", "ATRASADA", "status"),
                List.of("ate", "18/10/2026", "ate"),
                List.of("idosoId", "abc", "idosoId"))) {
            MockHttpServletRequestBuilder requisicao = get(ROTA)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR))
                    .param(caso.get(0), caso.get(1));
            if (caso.get(0).equals("de")) {
                requisicao.param("ate", D1.toString());
            }
            mvc.perform(requisicao)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.campos[0].campo").value(caso.get(2)));
        }
    }

    @Test
    void idosoDeOutroCuidadorEhNaoEncontrado() throws Exception {
        Long outroCuidador = cuidador();

        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(outroCuidador, Role.CUIDADOR))
                        .param("idosoId", idosoA.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        // Sem idosoId, o outro cuidador não vê nada destes idosos
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(outroCuidador, Role.CUIDADOR))
                        .param("de", D1.toString()).param("ate", D3.toString()))
                .andExpect(jsonPath("$.totalElementos").value(0));
    }

    @Test
    void medicamentoDeOutroCuidadorNoFiltroNaoTrazNada() throws Exception {
        Long remedioDoOutro = jdbc.queryForObject("SELECT medicamento_id FROM tratamento WHERE id = ?", Long.class,
                tratamentoDoOutro);

        mvc.perform(historico().param("medicamentoId", remedioDoOutro.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(0))
                .andExpect(jsonPath("$.conteudo", hasSize(0)));
    }

    @Test
    void papeisEContaExcluida() throws Exception {
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoA, Role.IDOSO)))
                .andExpect(status().isForbidden());
        mvc.perform(get(ROTA)).andExpect(status().isUnauthorized());
        Long excluido = cuidador();
        jdbc.update("DELETE FROM cuidador WHERE id = ?", excluido);
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(excluido, Role.CUIDADOR)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
    }

    @Test
    void tokenExpiradoOuMalformadoNasRotasNovasEh401() throws Exception {
        // Mesmo segredo, validade já vencida
        String expirado = new JwtTokenProvider(segredo, -60_000, -60_000).generateToken(cuidadorId, Role.CUIDADOR);
        List<MockHttpServletRequestBuilder> rotas = List.of(
                get(ROTA),
                get("/api/configuracoes/tolerancia"),
                put("/api/configuracoes/tolerancia").contentType(MediaType.APPLICATION_JSON).content("{\"toleranciaMinutos\": 30}"),
                get("/api/relatorios/diario").param("idosoId", idosoA.toString()));
        for (MockHttpServletRequestBuilder rota : rotas) {
            mvc.perform(rota.header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer nao.e.um.jwt")).andExpect(status().isUnauthorized());
        // Token de idoso cuja conta não existe mais, em rota de cuidador: barrado pelo papel
        Long idosoExcluido = idoso(cuidadorId, "Excluído");
        jdbc.update("DELETE FROM idoso WHERE id = ?", idosoExcluido);
        mvc.perform(get("/api/configuracoes/tolerancia")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoExcluido, Role.IDOSO)))
                .andExpect(status().isForbidden());
    }
}
