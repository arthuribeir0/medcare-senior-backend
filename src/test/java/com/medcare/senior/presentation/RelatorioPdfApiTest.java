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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.medcare.senior.infrastructure.relatorio.TextoDoPdf.paginas;
import static com.medcare.senior.infrastructure.relatorio.TextoDoPdf.semEspacos;
import static com.medcare.senior.infrastructure.relatorio.TextoDoPdf.texto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Histórico de adesão em PDF (UC-012, RN-012) pela API, contra o PostgreSQL real: arquivo e cabeçalhos, conteúdo lido
 * de volta, caso grande em várias páginas, período sem registros, autorização e parâmetros. Datas em 2000 para não
 * misturar com dados do banco local. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class RelatorioPdfApiTest {

    private static final String ROTA = "/api/relatorios/pdf";
    private static final LocalDate D1 = LocalDate.of(2000, 1, 10);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidadorId;
    private Long idosoId;
    private Long losartana;
    private Long tratamentoLosartana;
    private Long tratamentoMetformina;

    @BeforeEach
    void criarDados() {
        cuidadorId = cuidador();
        idosoId = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES (?, ?, ?) RETURNING id",
                Long.class, "Antônia Conceição " + UUID.randomUUID().toString().substring(0, 4), LocalDate.of(1945, 3, 12), cuidadorId);
        losartana = medicamento("Losartana", "50mg");
        tratamentoLosartana = tratamento(losartana);
        tratamentoMetformina = tratamento(medicamento("Metformina", "500mg"));
        dose(tratamentoLosartana, D1.atTime(8, 0), "CONFIRMADA_NO_HORARIO");
        dose(tratamentoLosartana, D1.atTime(20, 0), "CONFIRMADA_COM_ATRASO");
        dose(tratamentoLosartana, D1.plusDays(1).atTime(8, 0), "NAO_TOMADA");
        dose(tratamentoMetformina, D1.plusDays(1).atTime(12, 0), "CONFIRMADA_NO_HORARIO");
        dose(tratamentoMetformina, D1.plusDays(2).atTime(12, 0), "PENDENTE");
        // Outro idoso (de outro cuidador) no mesmo dia: nunca entra no arquivo
        Long outro = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES ('Alheio Zuleica', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1950, 1, 1), cuidador());
        Long remedioAlheio = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) VALUES "
                + "('Remedio Alheio', '1mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, outro);
        dose(tratamento(remedioAlheio), D1.atTime(9, 0), "CONFIRMADA_NO_HORARIO");
    }

    private Long cuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador pdf', ?, 'hash', now(), true, now()) RETURNING id",
                Long.class, "pdf-" + UUID.randomUUID() + "@teste.com");
    }

    private Long medicamento(String nome, String dosagem) {
        return jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) VALUES (?, ?, 'COMPRIMIDO', ?) "
                + "RETURNING id", Long.class, nome, dosagem, idosoId);
    }

    private Long tratamento(Long medicamento) {
        return jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class, medicamento, D1);
    }

    private void dose(Long tratamento, LocalDateTime horario, String status) {
        jdbc.update("INSERT INTO dose (tratamento_id, horario_programado, hora_confirmacao, status) VALUES (?, ?, ?, ?)",
                tratamento, horario, "CONFIRMADA_NO_HORARIO".equals(status) || "CONFIRMADA_COM_ATRASO".equals(status)
                        ? horario.plusMinutes(5) : null, status);
    }

    private MockHttpServletRequestBuilder pdf(String de, String ate) {
        MockHttpServletRequestBuilder r = get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR))
                .param("idosoId", idosoId.toString());
        if (de != null) {
            r.param("de", de);
        }
        if (ate != null) {
            r.param("ate", ate);
        }
        return r;
    }

    private String nomeDoIdoso() {
        return jdbc.queryForObject("SELECT nome FROM idoso WHERE id = ?", String.class, idosoId);
    }

    @Test
    void geraOPdfComCabecalhosSemDadoPessoalNoNomeEConteudoDaRn012() throws Exception {
        byte[] arquivo = mvc.perform(pdf(D1.toString(), D1.plusDays(2).toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"historico-adesao-" + idosoId + "-2000-01-10-a-2000-01-12.pdf\""))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, not(containsString("Ant"))))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();

        String texto = semEspacos(texto(arquivo));
        // (2 no horário + 1 com atraso) / (5 - 1 pendente) = 75,0%
        for (String esperado : List.of("Idoso: " + nomeDoIdoso(), "Período: 10/01/2000 a 12/01/2000",
                "Taxa de adesão: 75,0%", "5 2 1 1 1",
                "Losartana 50mg 3 1 1 1 0 66,7%", "Metformina 500mg 2 1 0 0 1 100,0%",
                "10/01/2000 08:00 Losartana 50mg Confirmada no horário 08:05",
                "10/01/2000 20:00 Losartana 50mg Confirmada com atraso 20:05",
                "11/01/2000 08:00 Losartana 50mg Não noHorario —",
                "11/01/2000 12:00 Metformina 500mg Confirmada no horário 12:05",
                "12/01/2000 12:00 Metformina 500mg Pendente —")) {
            assertThat(texto).contains(semEspacos(esperado));
        }
        assertThat(texto).doesNotContain("Alheio").doesNotContain("RemedioAlheio");
    }

    @Test
    void periodoGrandeComCentenasDeDosesEMedicamentoRemovidoViraVariasPaginas() throws Exception {
        List<Object[]> linhas = new ArrayList<>();
        LocalDateTime inicio = D1.plusDays(5).atTime(6, 0);
        for (int i = 0; i < 600; i++) {
            LocalDateTime horario = inicio.plusHours(6L * i);
            linhas.add(new Object[]{tratamentoLosartana, horario, horario.plusMinutes(2), "CONFIRMADA_NO_HORARIO"});
        }
        jdbc.batchUpdate("INSERT INTO dose (tratamento_id, horario_programado, hora_confirmacao, status) VALUES (?, ?, ?, ?)", linhas);
        // Remédio removido continua no histórico (RN-008)
        jdbc.update("UPDATE medicamento SET removido_em = now() WHERE id = ?", losartana);

        byte[] arquivo = mvc.perform(pdf(D1.toString(), "2000-12-31"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(paginas(arquivo)).isGreaterThan(10);
        String texto = semEspacos(texto(arquivo));
        assertThat(texto).contains(semEspacos("Losartana 50mg 603"))
                .contains(semEspacos("15/01/2000 06:00 Losartana 50mg Confirmada no horário 06:02"))
                .contains(semEspacos(inicio.plusHours(6L * 599).toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        + " 00:00 Losartana 50mg Confirmada no horário 00:02"));
    }

    @Test
    void periodoSemDosesEh422ComCodigo() throws Exception {
        mvc.perform(pdf("1990-01-01", "1990-12-31"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("PERIODO_SEM_REGISTROS"));
    }

    @Test
    void parametrosInvalidosSao400() throws Exception {
        mvc.perform(pdf(null, D1.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("de"));
        mvc.perform(pdf(D1.plusDays(1).toString(), D1.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                .andExpect(jsonPath("$.campos[0].campo").value("de"));
        mvc.perform(pdf("10/01/2000", null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR))
                        .param("de", D1.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autorizacao() throws Exception {
        // Idoso de outro cuidador: 404, como inexistente
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidador(), Role.CUIDADOR))
                        .param("idosoId", idosoId.toString()).param("de", D1.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        // Token de idoso: 403
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoId, Role.IDOSO))
                        .param("idosoId", idosoId.toString()).param("de", D1.toString()))
                .andExpect(status().isForbidden());
        // Sem token: 401
        mvc.perform(get(ROTA).param("idosoId", idosoId.toString()).param("de", D1.toString()))
                .andExpect(status().isUnauthorized());
        // Cuidador de conta excluída: 401 SESSAO_INVALIDA
        Long excluido = cuidador();
        jdbc.update("DELETE FROM cuidador WHERE id = ?", excluido);
        mvc.perform(get(ROTA).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(excluido, Role.CUIDADOR))
                        .param("idosoId", idosoId.toString()).param("de", D1.toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
    }
}
