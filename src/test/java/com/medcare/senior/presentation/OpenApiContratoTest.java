package com.medcare.senior.presentation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato publicado no OpenAPI (com a documentação ligada): os campos e valores que o app usa na dose e no relatório.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false",
        "app.docs.enabled=true"})
@AutoConfigureMockMvc
class OpenApiContratoTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void doseTemLimiteDeConfirmacaoQuemConfirmouEOsStatusNovos() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.DoseResponseDTO.properties.limiteConfirmacao.format")
                        .value("date-time"))
                .andExpect(jsonPath("$.components.schemas.DoseResponseDTO.properties.confirmadoPor.enum",
                        containsInAnyOrder("PACIENTE", "CUIDADOR")))
                .andExpect(jsonPath("$.components.schemas.DoseResponseDTO.properties.status.enum",
                        containsInAnyOrder("PENDENTE", "CONFIRMADA_NO_HORARIO", "NAO_TOMADA", "CONFIRMADA_COM_ATRASO")))
                .andExpect(jsonPath("$.components.schemas.DoseResponseDTO.properties.statusExibicao.enum",
                        not(hasItem("TOMADA"))))
                .andExpect(jsonPath("$.components.schemas.TotaisDTO.properties.confirmadasNoHorario").exists())
                .andExpect(jsonPath("$.components.schemas.TotaisDTO.properties.tomadas").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/doses/{id}/confirmar'].put").exists())
                .andExpect(jsonPath("$.paths['/api/relatorios/pdf'].get").exists());
    }
}
