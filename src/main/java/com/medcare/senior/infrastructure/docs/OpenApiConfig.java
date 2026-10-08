package com.medcare.senior.infrastructure.docs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI e esquema de segurança Bearer JWT (habilita o botão Authorize do Swagger UI).
 *
 * <p>Por padrão toda operação exige o token; endpoints públicos (cadastro, login, pareamento) anulam
 * esse requisito com {@code @SecurityRequirements()} vazio no controller.
 *
 * <p>A documentação só é servida com {@code app.docs.enabled=true} (variável DOCS_ENABLED); ver SecurityConfig.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MedCare Sênior API",
                version = "v1",
                description = "API do MedCare Sênior: cadastro de cuidadores e idosos, vínculo por código, "
                        + "medicamentos e tratamentos."),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_JWT))
@SecurityScheme(
        name = OpenApiConfig.BEARER_JWT,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtido em POST /api/auth/login (cuidador) ou POST /api/vinculo/validar (idoso)")
public class OpenApiConfig {

    public static final String BEARER_JWT = "bearerAuth";
}
