package com.medcare.senior.infrastructure.docs;

import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Completa o OpenAPI gerado com o que vale para toda a API, para que nenhuma operação fique sem isso:
 * <ul>
 *   <li>perfil exigido ({@link PerfilExigido}) no início da descrição e o cadeado do bearerAuth só nas rotas
 *       protegidas;</li>
 *   <li>401 (NAO_AUTENTICADO ou SESSAO_INVALIDA) e 403 (ACESSO_NEGADO) nas rotas protegidas e 500 (ERRO_INTERNO) em
 *       todas;</li>
 *   <li>corpo de erro padrão (ErroResponse) em toda resposta 4xx/5xx, com um exemplo para cada código citado na
 *       descrição da resposta, e o cabeçalho Retry-After no 429;</li>
 *   <li>a rota de saúde do actuator, na tag Saúde.</li>
 * </ul>
 */
@Component
public class DocumentacaoPadraoCustomizer implements GlobalOperationCustomizer, GlobalOpenApiCustomizer {

    static final String ERRO_RESPONSE_REF = "#/components/schemas/ErroResponse";
    static final String TAG_SAUDE = "Saúde";

    private static final Pattern CODIGO = Pattern.compile("\\b[A-Z][A-Z_]{4,}[A-Z]\\b");

    /** Código citado na descrição de uma resposta -> mensagem real que a API devolve com ele. */
    private static final Map<String, String> MENSAGENS = mensagens();

    /** Código usado no exemplo quando a descrição da resposta não cita nenhum. */
    private static final Map<String, String> CODIGO_PADRAO = Map.of(
            "400", "VALIDACAO",
            "401", "NAO_AUTENTICADO",
            "403", "ACESSO_NEGADO",
            "404", "RECURSO_NAO_ENCONTRADO",
            "409", "DOSE_JA_REGISTRADA",
            "422", "PERIODO_SEM_REGISTROS",
            "429", "LIMITE_TENTATIVAS",
            "500", "ERRO_INTERNO");

    private static final Map<String, String> RAZAO = Map.of(
            "400", "Bad Request", "401", "Unauthorized", "403", "Forbidden", "404", "Not Found", "405", "Method Not Allowed",
            "409", "Conflict", "422", "Unprocessable Content", "429", "Too Many Requests", "500", "Internal Server Error");

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        PerfilExigido anotacao = handlerMethod.getMethodAnnotation(PerfilExigido.class);
        if (anotacao == null) {
            // Sem a anotação a operação sai sem perfil na descrição; o OpenApiContratoTest acusa
            return operation;
        }
        Perfil perfil = anotacao.value();
        String descricao = operation.getDescription() == null ? "" : "\n\n" + operation.getDescription();
        operation.setDescription("**Perfil exigido:** " + perfil.rotulo() + "." + descricao);

        if (perfil.exigeToken()) {
            operation.setSecurity(List.of(new SecurityRequirement().addList(OpenApiConfig.BEARER_JWT)));
            garantir(operation, "401", "Sem token, ou token inválido ou expirado (NAO_AUTENTICADO); ou token de conta que "
                    + "não existe mais (SESSAO_INVALIDA)");
            garantir(operation, "403", "Token válido de outro perfil (ACESSO_NEGADO)");
        } else {
            operation.setSecurity(List.of());
        }
        garantir(operation, "500", "Erro inesperado, sem detalhes na resposta (ERRO_INTERNO)");
        return operation;
    }

    @Override
    public void customise(OpenAPI openApi) {
        openApi.getPaths().forEach((caminho, item) -> item.readOperations().forEach(this::padronizarErros));
        adicionarSaude(openApi);
    }

    private static void garantir(Operation operation, String codigo, String descricao) {
        if (operation.getResponses() == null) {
            operation.setResponses(new ApiResponses());
        }
        operation.getResponses().computeIfAbsent(codigo, c -> new ApiResponse().description(descricao));
    }

    /** Toda resposta 4xx/5xx com o ErroResponse e um exemplo por código citado na descrição. */
    private void padronizarErros(Operation operation) {
        if (operation.getResponses() == null) {
            return;
        }
        operation.getResponses().forEach((status, resposta) -> {
            if (!status.startsWith("4") && !status.startsWith("5")) {
                return;
            }
            MediaType json = new MediaType().schema(new Schema<>().$ref(ERRO_RESPONSE_REF));
            exemplos(status, resposta.getDescription()).forEach(json::addExamples);
            resposta.setContent(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, json));
            if ("429".equals(status)) {
                resposta.addHeaderObject("Retry-After", new Header()
                        .description("Segundos até o próximo pedido ser aceito")
                        .schema(new IntegerSchema().example(60)));
            }
        });
    }

    private static Map<String, Example> exemplos(String status, String descricao) {
        Map<String, Example> exemplos = new LinkedHashMap<>();
        Matcher matcher = CODIGO.matcher(descricao == null ? "" : descricao);
        while (matcher.find()) {
            String codigo = matcher.group();
            if (MENSAGENS.containsKey(codigo)) {
                exemplos.putIfAbsent(codigo, exemplo(status, codigo));
            }
        }
        if (exemplos.isEmpty() && CODIGO_PADRAO.containsKey(status)) {
            String codigo = CODIGO_PADRAO.get(status);
            exemplos.put(codigo, exemplo(status, codigo));
        }
        return exemplos;
    }

    private static Example exemplo(String status, String codigo) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("timestamp", "2026-10-08T14:30:00");
        corpo.put("status", Integer.parseInt(status));
        corpo.put("erro", RAZAO.getOrDefault(status, ""));
        corpo.put("codigo", codigo);
        corpo.put("mensagem", MENSAGENS.get(codigo));
        // Mapas com ordem fixa: o openapi.json exportado precisa sair igual em toda execução
        Map<String, Object> campo = new LinkedHashMap<>();
        campo.put("campo", "email");
        campo.put("mensagem", "O e-mail informado é inválido");
        corpo.put("campos", "VALIDACAO".equals(codigo) ? List.of(campo) : List.of());
        return new Example().summary(codigo).value(corpo);
    }

    /** GET /actuator/health não passa pelo springdoc (actuator); publicado aqui, na tag Saúde. */
    private static void adicionarSaude(OpenAPI openApi) {
        if (openApi.getTags() == null || openApi.getTags().stream().noneMatch(t -> TAG_SAUDE.equals(t.getName()))) {
            openApi.addTagsItem(new Tag().name(TAG_SAUDE).description("Verificação de saúde para o HEALTHCHECK do contêiner"));
        }
        Schema<?> status = new ObjectSchema().addProperty("status",
                new StringSchema()._enum(List.of("UP", "DOWN")).example("UP"));
        Operation saude = new Operation()
                .addTagsItem(TAG_SAUDE)
                .operationId("saude")
                .summary("Saúde da aplicação")
                .description("**Perfil exigido:** público, sem token.\n\nSó o status, sem detalhes de componentes. "
                        + "DOWN (503) quando o banco não responde.")
                .security(List.of())
                .responses(new ApiResponses()
                        .addApiResponse("200", new ApiResponse().description("Aplicação e banco no ar")
                                .content(new Content().addMediaType("application/json", new MediaType().schema(status))))
                        .addApiResponse("503", new ApiResponse().description("Banco fora do ar")
                                .content(new Content().addMediaType("application/json", new MediaType().schema(status)))));
        openApi.path("/actuator/health", new PathItem().get(saude));
    }

    private static Map<String, String> mensagens() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("VALIDACAO", "Um ou mais campos são inválidos");
        m.put("CORPO_INVALIDO", "Corpo da requisição inválido: verifique se é um JSON válido em UTF-8 e se os campos estão "
                + "no formato esperado");
        m.put("REQUISICAO_INVALIDA", "Requisição inválida");
        m.put("SENHA_FRACA", "A senha deve ter no mínimo 8 caracteres, com ao menos uma letra maiúscula, uma minúscula e um "
                + "número");
        m.put("CONSENTIMENTO_OBRIGATORIO", "É necessário aceitar o termo de consentimento de dados (LGPD) para concluir o "
                + "cadastro");
        m.put("TOKEN_DISPOSITIVO_INVALIDO", "O token do dispositivo é obrigatório e deve ter no máximo 512 caracteres");
        m.put("CODIGO_INVALIDO", "Código inválido ou expirado");
        m.put("DADOS_OBRIGATORIOS_MEDICAMENTO", "Nome, dosagem e tipo do medicamento são obrigatórios");
        m.put("TRATAMENTO_INVALIDO", "Informe ao menos um horário para o tratamento");
        m.put("CREDENCIAIS_INVALIDAS", "E-mail ou senha inválidos");
        m.put("NAO_AUTENTICADO", "Autenticação necessária. Envie um token válido no cabeçalho Authorization.");
        m.put("SESSAO_INVALIDA", "Sessão inválida. Entre na sua conta de novo.");
        m.put("ACESSO_NEGADO", "Acesso negado para este perfil.");
        m.put("RECURSO_NAO_ENCONTRADO", "Recurso não encontrado");
        m.put("METODO_NAO_PERMITIDO", "Método HTTP não permitido para esta rota");
        m.put("EMAIL_DUPLICADO", "Já existe um cuidador cadastrado com o e-mail: maria@exemplo.com");
        m.put("PACIENTE_DUPLICADO", "Já existe um paciente com este nome e data de nascimento vinculado a este cuidador");
        m.put("TRATAMENTO_ATIVO", "O medicamento possui tratamento ativo. Para removê-lo mesmo assim, use forcarRemocao=true");
        m.put("DOSE_JA_REGISTRADA", "Esta dose já foi registrada.");
        m.put("DOSE_MUITO_CEDO", "Ainda é cedo para confirmar esta dose.");
        m.put("DOSE_ENCERRADA", "O prazo para confirmar esta dose terminou.");
        m.put("PERIODO_SEM_REGISTROS", "Não há doses registradas neste período. Escolha outro período para exportar.");
        m.put("LIMITE_TENTATIVAS", "Muitas tentativas inválidas. Tente novamente mais tarde.");
        m.put("ERRO_INTERNO", "Erro interno. Tente novamente mais tarde.");
        return Map.copyOf(m);
    }

    /** Códigos com exemplo publicado (o teste de contrato confere que cobrem todo o CodigoErro). */
    public static Set<String> codigosComExemplo() {
        return MENSAGENS.keySet();
    }
}
