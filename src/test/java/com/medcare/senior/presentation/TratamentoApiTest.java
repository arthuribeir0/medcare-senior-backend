package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastro e edição de tratamento pela API, com o relógio fixo em quinta-feira, 08/10/2026, às 10:00: o 400 do horário
 * agora ou a menos de 5 minutos (corpo exato) e a primeiraDose na resposta.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class TratamentoApiTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 10, 0);
    private static final String MENSAGEM = "Não é possível cadastrar um horário que seja agora ou daqui a menos de 5 "
            + "minutos. Escolha um horário com pelo menos 5 minutos de diferença do horário atual.";

    @TestConfiguration
    static class RelogioFixo {
        @Bean
        @Primary
        Clock relogioFixo() {
            return Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private Long cuidador;
    private Long medicamento;

    @BeforeEach
    void criarDados() {
        cuidador = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd) "
                + "VALUES ('Ana', ?, 'hash', ?, true) RETURNING id", Long.class, "trat-" + UUID.randomUUID() + "@teste.com", AGORA);
        Long paciente = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) "
                + "VALUES ('José', '1945-03-12', ?) RETURNING id", Long.class, cuidador);
        medicamento = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, paciente_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, paciente);
    }

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder requisicao, String corpo) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidador, Role.CUIDADOR))
                .contentType(MediaType.APPLICATION_JSON).content(corpo);
    }

    private static String tratamento(String horarios, String dias) {
        return "{\"dataInicio\": \"2026-10-08\", \"horarios\": [" + horarios + "], \"diasSemana\": [" + dias + "]}";
    }

    @Test
    void horarioAgoraOuAMenosDeCincoMinutosResponde400ComAMensagemNoTopoENoCampo() throws Exception {
        for (String horario : new String[]{"\"10:00\"", "\"10:04\""}) {
            mvc.perform(comoCuidador(post("/api/medicamentos/" + medicamento + "/tratamentos"),
                            tratamento(horario, "\"THURSDAY\"")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.codigo").value("VALIDACAO"))
                    .andExpect(jsonPath("$.mensagem").value(MENSAGEM))
                    .andExpect(jsonPath("$.campos[0].campo").value("horarios"))
                    .andExpect(jsonPath("$.campos[0].mensagem").value(MENSAGEM));
        }
    }

    @Test
    void horarioACincoMinutosEhAceitoComAPrimeiraDoseHoje() throws Exception {
        mvc.perform(comoCuidador(post("/api/medicamentos/" + medicamento + "/tratamentos"),
                        tratamento("\"10:05\"", "\"THURSDAY\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primeiraDose").value("2026-10-08T10:05:00"));
    }

    @Test
    void horarioQueJaPassouHojeEhAceitoComAPrimeiraDoseNoProximoDiaValido() throws Exception {
        mvc.perform(comoCuidador(post("/api/medicamentos/" + medicamento + "/tratamentos"),
                        tratamento("\"08:00\"", "\"THURSDAY\", \"FRIDAY\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primeiraDose").value("2026-10-09T08:00:00"));
    }

    @Test
    void edicaoSoValidaOsHorariosNovosEAListagemNaoTrazPrimeiraDose() throws Exception {
        String criado = mvc.perform(comoCuidador(post("/api/medicamentos/" + medicamento + "/tratamentos"),
                        tratamento("\"08:00\"", "\"THURSDAY\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = criado.replaceAll(".*\"id\"\\s*:\\s*(\\d+).*", "$1");

        mvc.perform(comoCuidador(put("/api/tratamentos/" + id),
                        "{\"horarios\": [\"08:00\", \"10:03\"], \"diasSemana\": [\"THURSDAY\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").value(MENSAGEM));

        mvc.perform(comoCuidador(put("/api/tratamentos/" + id),
                        "{\"dataFim\": \"2026-12-31\", \"horarios\": [\"08:00\"], \"diasSemana\": [\"THURSDAY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataFim").value("2026-12-31"))
                .andExpect(jsonPath("$.primeiraDose").value("2026-10-15T08:00:00"));

        mvc.perform(comoCuidador(get("/api/medicamentos/" + medicamento + "/tratamentos"), ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].primeiraDose").value(nullValue()));
    }
}
