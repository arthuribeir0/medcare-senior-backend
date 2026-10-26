package com.medcare.senior.infrastructure.docs;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Configuration;

/**
 * Metadados do OpenAPI, tags na ordem de leitura e esquema de segurança Bearer JWT (botão Authorize do Swagger UI).
 *
 * <p>O perfil de cada operação vem de {@link PerfilExigido}; o {@link DocumentacaoPadraoCustomizer} aplica o cadeado
 * só nas rotas protegidas e completa as respostas de erro.
 *
 * <p>A documentação é servida com {@code app.docs.enabled=true} (variável SWAGGER_ENABLED): ligada por padrão fora do
 * perfil prod e desligada no prod, salvo se a variável disser o contrário.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "MedCare Sênior API",
                version = "v1",
                description = """
                        API do MedCare Sênior: lembrete e registro das doses de remédio de pacientes, com aviso ao \
                        cuidador quando uma dose não é confirmada.

                        **Perfis.** CUIDADOR (token de POST /api/auth/login, validade de 24 horas) cadastra pacientes, \
                        remédios e tratamentos e acompanha tudo. PACIENTE (token de POST /api/vinculo/validar, validade \
                        de 180 dias) usa o aparelho pareado para ver e confirmar as próprias doses e conversar com o \
                        cuidador. Cada operação diz o perfil exigido; sem token, 401; com token de outro perfil, 403.

                        **Erros.** Toda resposta de erro usa o mesmo corpo (ErroResponse). O app decide pelo campo \
                        `codigo`, que é estável; a `mensagem` é para pessoas e pode mudar. Em erro de validação, \
                        `campos` lista cada campo inválido.

                        **Limite de tentativas.** O pareamento bloqueia o IP depois de 5 códigos inválidos em 15 \
                        minutos (429 LIMITE_TENTATIVAS, com Retry-After). Em produção, o nginx também limita a API a \
                        20 pedidos por segundo por IP (rajada de 80); acima disso, o próprio nginx responde 429 com \
                        `codigo` LIMITE_TENTATIVAS, `mensagem` e `campos` (sem `timestamp`, `status` e `erro`).

                        **Janela da dose** (calculada no servidor, no fuso da aplicação): abre 60 minutos antes do \
                        horário programado; o aviso ao cuidador sai no fim da tolerância (padrão de 30 minutos, de 5 a \
                        120 por cuidador); a dose pode ser confirmada até tolerância + 30 minutos depois do horário, e \
                        então vira NAO_TOMADA. Até o aviso, a confirmação fica CONFIRMADA_NO_HORARIO; depois, \
                        CONFIRMADA_COM_ATRASO.

                        **Datas e horas** sem fuso (ISO 8601 local, ex.: 2026-10-08T14:30:00), no fuso da aplicação."""),
        security = @SecurityRequirement(name = OpenApiConfig.BEARER_JWT),
        tags = {
                @Tag(name = "Autenticação", description = "Login do cuidador"),
                @Tag(name = "Cuidador", description = "Cadastro, dados e exclusão da conta do cuidador"),
                @Tag(name = "Pacientes", description = "Pacientes do cuidador, doses do dia e código de vínculo; e as "
                        + "leituras do próprio paciente no aparelho pareado"),
                @Tag(name = "Vínculo", description = "Pareamento do aparelho do paciente por código"),
                @Tag(name = "Medicamentos e tratamentos", description = "Remédios dos pacientes e os períodos, horários e "
                        + "dias da semana de cada um"),
                @Tag(name = "Doses", description = "Confirmação de dose pelo paciente ou pelo cuidador dele"),
                @Tag(name = "Configurações", description = "Tolerância do aviso de dose não confirmada, por cuidador"),
                @Tag(name = "Relatórios", description = "Relatório diário e histórico de adesão em PDF"),
                @Tag(name = "Histórico", description = "Histórico de doses dos pacientes do cuidador, com filtros"),
                @Tag(name = "Mensagens", description = "Conversa entre o cuidador e cada paciente"),
                @Tag(name = "Push/Token", description = "Token do aparelho do cuidador para as notificações push (FCM)"),
                @Tag(name = DocumentacaoPadraoCustomizer.TAG_SAUDE, description = "Verificação de saúde para o "
                        + "HEALTHCHECK do contêiner")
        })
@SecurityScheme(
        name = OpenApiConfig.BEARER_JWT,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token obtido em POST /api/auth/login (cuidador) ou POST /api/vinculo/validar (paciente)")
public class OpenApiConfig {

    public static final String BEARER_JWT = "bearerAuth";
}
