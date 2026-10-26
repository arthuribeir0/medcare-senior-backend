package com.medcare.senior.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Perfil prod sem SWAGGER_ENABLED: a documentação não existe (404, não 401), mesmo com o nome antigo DOCS_ENABLED
 * ligado. O resto da API continua protegido como sempre.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false",
        "jwt.secret=segredo-do-teste-do-perfil-prod-com-mais-de-32-caracteres", "DOCS_ENABLED=true"})
@ActiveProfiles("prod")
@AutoConfigureMockMvc
class SwaggerDesligadoEmProducaoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void semAVariavelADocumentacaoResponde404() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/api/pacientes")).andExpect(status().isUnauthorized());
    }
}
