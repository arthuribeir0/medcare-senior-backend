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
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Desvinculação de paciente (DELETE /api/pacientes/{id}) de ponta a ponta: segurança, controller, use case e
 * PostgreSQL real. Cada teste roda numa transação desfeita no final. A atomicidade (falha no meio) fica em
 * {@link DesvinculacaoAtomicaTest}, que precisa de transações de verdade.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class DesvinculacaoApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    private DadosDeDesvinculacao dados;
    private Long cuidadorA;
    private Long cuidadorB;

    @BeforeEach
    void criarDados() {
        dados = new DadosDeDesvinculacao(jdbc, LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
        cuidadorA = dados.cuidador("Ana Souza");
        cuidadorB = dados.cuidador("Bruno Lima");
    }

    @Test
    void desvincularApagaOPacienteEOsDadosDeleEAnonimizaAsDosesRegistradas() throws Exception {
        DadosDeDesvinculacao.Paciente jose = dados.pacienteCompleto(cuidadorA, "José Souza");
        DadosDeDesvinculacao.Paciente clara = dados.pacienteCompleto(cuidadorA, "Clara Souza");
        DadosDeDesvinculacao.Paciente helena = dados.pacienteCompleto(cuidadorB, "Helena Lima");

        mvc.perform(comoCuidador(delete("/api/pacientes/" + jose.id()), cuidadorA)).andExpect(status().isNoContent());

        assertThat(dados.contar("paciente", "id", jose.id())).isZero();
        assertThat(dados.contar("medicamento", "paciente_id", jose.id())).isZero();
        assertThat(dados.contar("tratamento", "id", jose.tratamentoId())).isZero();
        assertThat(dados.contar("codigo_vinculo", "paciente_id", jose.id())).isZero();
        assertThat(dados.contar("mensagem", "paciente_id", jose.id())).isZero();
        assertThat(dados.contar("pedido_desconexao", "paciente_id", jose.id())).isZero();
        assertThat(dados.contar("dose", "id", jose.dosePendenteId())).isZero();
        assertThat(dados.contar("evento_notificacao", "dose_id", jose.doseRegistradaId())).isZero();
        // Registrada: mantida, mas sem vínculo com ninguém (ON DELETE SET NULL)
        assertThat(dados.contar("dose", "id", jose.doseRegistradaId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT tratamento_id FROM dose WHERE id = ?", Long.class, jose.doseRegistradaId()))
                .isNull();

        // O outro paciente do mesmo cuidador, o cuidador e o paciente de outra conta continuam inteiros
        assertThat(dados.contar("cuidador", "id", cuidadorA)).isEqualTo(1);
        dados.assertIntacto(clara);
        dados.assertIntacto(helena);
        mvc.perform(comoCuidador(get("/api/pacientes"), cuidadorA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(clara.id()));
    }

    @Test
    void depoisDeDesvincularOPacienteSome404EOAparelhoPerdeASessao() throws Exception {
        DadosDeDesvinculacao.Paciente jose = dados.pacienteCompleto(cuidadorA, "José Souza");
        String tokenDoAparelho = tokens.generateToken(jose.id(), Role.PACIENTE);
        mvc.perform(get("/api/pacientes/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoAparelho))
                .andExpect(status().isOk());

        mvc.perform(comoCuidador(delete("/api/pacientes/" + jose.id()), cuidadorA)).andExpect(status().isNoContent());

        mvc.perform(get("/api/pacientes/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDoAparelho))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("SESSAO_INVALIDA"));
        mvc.perform(comoCuidador(get("/api/pacientes/" + jose.id()), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        // Repetir não é erro de servidor: o paciente já não existe
        mvc.perform(comoCuidador(delete("/api/pacientes/" + jose.id()), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    void pacienteDeOutroCuidadorOuInexistenteResponde404ENadaMuda() throws Exception {
        DadosDeDesvinculacao.Paciente helena = dados.pacienteCompleto(cuidadorB, "Helena Lima");

        mvc.perform(comoCuidador(delete("/api/pacientes/" + helena.id()), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));
        mvc.perform(comoCuidador(delete("/api/pacientes/" + Long.MAX_VALUE), cuidadorA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NAO_ENCONTRADO"));

        dados.assertIntacto(helena);
    }

    @Test
    void pacienteNaoDesvinculaNinguemESemTokenResponde401() throws Exception {
        DadosDeDesvinculacao.Paciente jose = dados.pacienteCompleto(cuidadorA, "José Souza");

        mvc.perform(delete("/api/pacientes/" + jose.id())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(jose.id(), Role.PACIENTE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACESSO_NEGADO"));
        mvc.perform(delete("/api/pacientes/" + jose.id()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));

        dados.assertIntacto(jose);
    }

    private MockHttpServletRequestBuilder comoCuidador(MockHttpServletRequestBuilder requisicao, Long cuidadorId) {
        return requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR));
    }
}
