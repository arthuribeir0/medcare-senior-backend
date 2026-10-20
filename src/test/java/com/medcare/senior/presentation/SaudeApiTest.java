package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Saúde mínima para o HEALTHCHECK do contêiner: GET /actuator/health sem token, só o status (sem componentes nem
 * detalhes do banco). Todo o resto do actuator continua fechado, inclusive com token.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
class SaudeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Test
    void saudeSemTokenDevolveSoOStatus() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$", aMapWithSize(1)));
    }

    @Test
    void demaisEndpointsDoActuatorFicamFechadosMesmoComToken() throws Exception {
        String token = "Bearer " + tokens.generateToken(1L, Role.CUIDADOR);
        for (String rota : new String[]{"/actuator", "/actuator/info", "/actuator/env", "/actuator/metrics",
                "/actuator/health/db", "/actuator/health/liveness", "/actuator/beans"}) {
            mvc.perform(get(rota)).andExpect(status().is4xxClientError());
            mvc.perform(get(rota).header(HttpHeaders.AUTHORIZATION, token)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/actuator/health")).andExpect(status().is4xxClientError());
    }
}
