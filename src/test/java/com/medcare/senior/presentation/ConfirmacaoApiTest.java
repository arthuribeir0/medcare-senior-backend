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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirmação de dose pela API, ponta a ponta no PostgreSQL real: cada recusa com o seu código (antes da janela,
 * depois do encerramento, remédio removido, dose inexistente), confirmação com atraso e o que fica gravado.
 * Janela padrão: abre 60 min antes, tolerância de 30 min, encerra 30 min depois do aviso. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class ConfirmacaoApiTest {

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
    private Long idosoId;
    private Long medicamentoId;
    private Long tratamentoId;

    @BeforeEach
    void criarDados() {
        agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        cuidadorId = jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, "
                + "consentimento_lgpd, data_consentimento) VALUES ('Cuidador confirmação', ?, 'hash', ?, true, ?) "
                + "RETURNING id", Long.class, "confirmacao-" + UUID.randomUUID() + "@teste.com", agora, agora);
        idosoId = jdbc.queryForObject("INSERT INTO idoso (nome, data_nascimento, cuidador_id) VALUES ('Maria', ?, ?) "
                + "RETURNING id", Long.class, LocalDate.of(1945, 3, 12), cuidadorId);
        medicamentoId = jdbc.queryForObject("INSERT INTO medicamento (nome, dosagem, tipo, idoso_id) "
                + "VALUES ('Losartana', '50mg', 'COMPRIMIDO', ?) RETURNING id", Long.class, idosoId);
        tratamentoId = jdbc.queryForObject("INSERT INTO tratamento (medicamento_id, data_inicio, horarios, dias_semana) "
                + "VALUES (?, ?, '[\"08:00\"]'::jsonb, '[\"MONDAY\"]'::jsonb) RETURNING id", Long.class,
                medicamentoId, agora.toLocalDate().minusDays(2));
    }

    private Long dosePendente(LocalDateTime horario) {
        return jdbc.queryForObject("INSERT INTO dose (tratamento_id, horario_programado, status) VALUES (?, ?, 'PENDENTE') "
                + "RETURNING id", Long.class, tratamentoId, horario);
    }

    private ResultActions confirmar(Long doseId) throws Exception {
        return mvc.perform(put("/api/doses/" + doseId + "/confirmar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(idosoId, Role.IDOSO)));
    }

    private ResultActions confirmarComoCuidador(Long doseId, Long cuidador) throws Exception {
        return mvc.perform(put("/api/doses/" + doseId + "/confirmar")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidador, Role.CUIDADOR)));
    }

    private Long outroCuidador() {
        return jdbc.queryForObject("INSERT INTO cuidador (nome, email, senha_hash, data_cadastro, consentimento_lgpd, "
                + "data_consentimento) VALUES ('Outro cuidador', ?, 'hash', ?, true, ?) RETURNING id", Long.class,
                "outro-" + UUID.randomUUID() + "@teste.com", agora, agora);
    }

    private String confirmadoPor(Long doseId) {
        return jdbc.queryForObject("SELECT confirmado_por FROM dose WHERE id = ?", String.class, doseId);
    }

    private String statusGravado(Long doseId) {
        return jdbc.queryForObject("SELECT status FROM dose WHERE id = ?", String.class, doseId);
    }

    @Test
    void antesDaAberturaEh409DoseMuitoCedoENadaMuda() throws Exception {
        Long dose = dosePendente(agora.plusMinutes(61));

        confirmar(dose)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_MUITO_CEDO"));
        assertThat(statusGravado(dose)).isEqualTo("PENDENTE");
    }

    @Test
    void depoisDoEncerramentoEh409DoseEncerradaENadaMuda() throws Exception {
        Long dose = dosePendente(agora.minusMinutes(61));

        confirmar(dose)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_ENCERRADA"));
        assertThat(statusGravado(dose)).isEqualTo("PENDENTE");
    }

    @Test
    void depoisDaToleranciaEhConfirmadaComAtrasoEGravada() throws Exception {
        Long dose = dosePendente(agora.minusMinutes(45));

        confirmar(dose)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA_COM_ATRASO"))
                .andExpect(jsonPath("$.statusExibicao").value("CONFIRMADA_COM_ATRASO"))
                .andExpect(jsonPath("$.confirmabilidade").value("JA_REGISTRADA"))
                .andExpect(jsonPath("$.confirmadoPor").value("IDOSO"))
                .andExpect(jsonPath("$.horaConfirmacao").exists());
        assertThat(statusGravado(dose)).isEqualTo("CONFIRMADA_COM_ATRASO");
        assertThat(confirmadoPor(dose)).isEqualTo("IDOSO");
        // Já registrada: a segunda tentativa não muda nada
        confirmar(dose)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_JA_REGISTRADA"));
    }

    @Test
    void doseDeRemedioRemovidoOuInexistenteEh404() throws Exception {
        Long dose = dosePendente(agora);
        jdbc.update("UPDATE medicamento SET removido_em = ? WHERE id = ?", agora, medicamentoId);

        confirmar(dose)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        confirmar(Long.MAX_VALUE)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        assertThat(statusGravado(dose)).isEqualTo("PENDENTE");
    }

    // ---- confirmação pelo cuidador --------------------------------------------------------------------------------

    @Test
    void cuidadorConfirmaDoseDoIdosoDeleEGravaQuemConfirmou() throws Exception {
        Long dose = dosePendente(agora);

        confirmarComoCuidador(dose, cuidadorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dose))
                .andExpect(jsonPath("$.status").value("CONFIRMADA_NO_HORARIO"))
                .andExpect(jsonPath("$.confirmadoPor").value("CUIDADOR"))
                .andExpect(jsonPath("$.horaConfirmacao").exists())
                .andExpect(jsonPath("$.limiteConfirmacao").value(agora.plusMinutes(60) + ":00"));
        assertThat(statusGravado(dose)).isEqualTo("CONFIRMADA_NO_HORARIO");
        assertThat(confirmadoPor(dose)).isEqualTo("CUIDADOR");

        // Já confirmada pelo cuidador: o idoso (e o próprio cuidador) recebem 409, nada muda
        confirmar(dose)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_JA_REGISTRADA"));
        confirmarComoCuidador(dose, cuidadorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_JA_REGISTRADA"));
        assertThat(confirmadoPor(dose)).isEqualTo("CUIDADOR");
    }

    @Test
    void cuidadorSegueAMesmaJanela() throws Exception {
        confirmarComoCuidador(dosePendente(agora.plusMinutes(61)), cuidadorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_MUITO_CEDO"));
        confirmarComoCuidador(dosePendente(agora.minusMinutes(61)), cuidadorId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("DOSE_ENCERRADA"));
        Long comAtraso = dosePendente(agora.minusMinutes(45));
        confirmarComoCuidador(comAtraso, cuidadorId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMADA_COM_ATRASO"));
    }

    @Test
    void cuidadorDeOutroIdosoRecebeOMesmo404DeDoseInexistente() throws Exception {
        Long dose = dosePendente(agora);
        Long outro = outroCuidador();

        String alheia = confirmarComoCuidador(dose, outro)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"))
                .andReturn().getResponse().getContentAsString();
        String inexistente = confirmarComoCuidador(Long.MAX_VALUE, outro)
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Mesma resposta (a menos do instante): não revela que a dose existe
        assertThat(alheia.replaceAll("\"timestamp\":\"[^\"]*\"", ""))
                .isEqualTo(inexistente.replaceAll("\"timestamp\":\"[^\"]*\"", ""));
        assertThat(statusGravado(dose)).isEqualTo("PENDENTE");
        assertThat(confirmadoPor(dose)).isNull();
    }

}
