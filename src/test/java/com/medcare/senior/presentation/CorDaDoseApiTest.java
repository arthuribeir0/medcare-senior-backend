package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cor do medicamento nas doses devolvidas pela API (doses do dia nos dois papéis, confirmação e histórico), contra o
 * PostgreSQL real. Medicamento sem cor devolve {@code cor: null}. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class CorDaDoseApiTest {

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
    private Long pacienteId;
    private Long doseAzul;

    @BeforeEach
    void criarDados() {
        agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Cuidador cor', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "cor-" + UUID.randomUUID() + "@teste.com", agora, agora);
        pacienteId = jdbc.queryForObject("INSERT INTO paciente (nome, data_nascimento, cuidador_id) VALUES ('Maria', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1945, 3, 12), cuidadorId);
        doseAzul = dose(medicamento("Losartana", "Azul-claro"), agora);
        // Mesmo horário, id maior: vem depois na ordem (horário, id)
        dose(medicamento("Metformina", null), agora);
    }

    private Long medicamento(String nome, String cor) {
        return jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, cor, paciente_id) "
                + "VALUES (?, '50mg', 'COMPRIMIDO', ?, ?) RETURNING id", Long.class, nome, cor, pacienteId);
    }

    private Long dose(Long medicamentoId, LocalDateTime horario) {
        Long tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, horario.toLocalDate().minusDays(1));
        return jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (?, ?, 'PENDENTE') "
                + "RETURNING id", Long.class, tratamentoId, horario);
    }

    private MockHttpServletRequestBuilder como(MockHttpServletRequestBuilder requisicao, Long id, Role papel) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(id, papel));
    }

    @Test
    void dosesDoDiaTrazemACorNosDoisPapeis() throws Exception {
        mvc.perform(como(get("/api/pacientes/" + pacienteId + "/doses"), cuidadorId, Role.CUIDADOR))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].medicamentoNome", contains("Losartana", "Metformina")))
                .andExpect(jsonPath("$[0].cor").value("Azul-claro"))
                .andExpect(jsonPath("$[1].cor").value(nullValue()));
        mvc.perform(como(get("/api/pacientes/me/doses"), pacienteId, Role.PACIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cor").value("Azul-claro"))
                .andExpect(jsonPath("$[1].cor").value(nullValue()));
    }

    @Test
    void confirmacaoDevolveADoseComACor() throws Exception {
        mvc.perform(como(put("/api/doses/" + doseAzul + "/confirmar"), pacienteId, Role.PACIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA_NO_HORARIO"))
                .andExpect(jsonPath("$.cor").value("Azul-claro"));
    }

    @Test
    void historicoTrazACor() throws Exception {
        mvc.perform(como(get("/api/historico"), cuidadorId, Role.CUIDADOR)
                        .param("pacienteId", pacienteId.toString())
                        .param("de", agora.toLocalDate().toString()).param("ate", agora.toLocalDate().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                // Do mais recente para o mais antigo: mesmo horário, id maior primeiro
                .andExpect(jsonPath("$.conteudo[0].cor").value(nullValue()))
                .andExpect(jsonPath("$.conteudo[1].cor").value("Azul-claro"));
    }
}
