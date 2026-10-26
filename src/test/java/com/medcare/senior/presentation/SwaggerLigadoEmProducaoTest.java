package com.medcare.senior.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil prod com SWAGGER_ENABLED=true (decisão explícita de quem implanta): a documentação responde sem token, e só
 * ela fica pública; as rotas da API continuam exigindo token.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false",
        "jwt.secret=segredo-do-teste-do-perfil-prod-com-mais-de-32-caracteres", "SWAGGER_ENABLED=true"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class SwaggerLigadoEmProducaoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void comAVariavelADocumentacaoRespondeSemToken() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("MedCare Sênior API"));
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("swagger")));
        mvc.perform(get("/api/pacientes")).andExpect(status().isUnauthorized());
    }
}
