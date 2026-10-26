package com.medcare.senior.presentation;

import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.docs.DocumentacaoPadraoCustomizer;
import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import com.medcare.senior.presentation.exception.CodigoErro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrato publicado no OpenAPI, com a documentação no padrão (ligada fora do perfil prod). Falha se uma rota nova
 * ficar sem resumo, sem perfil, sem resposta de erro documentada ou com o cadeado errado, se a anotação de perfil
 * divergir do SecurityConfig, ou se o docs/api/openapi.json ficar desatualizado (para regerar:
 * {@code mvnw test -Dtest=OpenApiContratoTest -Dopenapi.atualizar=true}).
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class OpenApiContratoTest {

    private static final Path ARQUIVO_EXPORTADO = Path.of("docs", "api", "openapi.json");
    private static final Set<String> TAGS = Set.of("Autenticação", "Cuidador", "Pacientes", "Vínculo",
            "Medicamentos e tratamentos", "Doses", "Configurações", "Relatórios", "Histórico", "Mensagens", "Push/Token",
            "Saúde");
    private static final String ERRO_REF = "#/components/schemas/ErroResponse";
    private static final Long ID_INEXISTENTE = Long.MAX_VALUE;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JsonMapper json;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeamentos;

    private JsonNode contrato;

    @BeforeEach
    void carregarContrato() throws Exception {
        String corpo = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        contrato = json.readTree(corpo);
    }

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

    @Test
    void todaOperacaoTemResumoPerfilTagEErroNoCorpoPadrao() {
        List<String> problemas = new ArrayList<>();
        Set<String> tagsUsadas = new HashSet<>();
        operacoes().forEach((chave, operacao) -> {
            if (texto(operacao, "summary").isBlank()) {
                problemas.add(chave + ": sem resumo");
            }
            if (!texto(operacao, "description").startsWith("**Perfil exigido:**")) {
                problemas.add(chave + ": descrição sem o perfil exigido (falta @PerfilExigido?)");
            }
            JsonNode tags = operacao.path("tags");
            if (tags.size() != 1 || !TAGS.contains(tags.get(0).asString())) {
                problemas.add(chave + ": precisa de exatamente uma tag da lista, veio " + tags);
            } else {
                tagsUsadas.add(tags.get(0).asString());
            }
            boolean temErro = false;
            for (Map.Entry<String, JsonNode> resposta : operacao.path("responses").properties()) {
                if (resposta.getKey().startsWith("4") || resposta.getKey().startsWith("5")) {
                    temErro = true;
                    if (!chave.endsWith("/actuator/health")
                            && !ERRO_REF.equals(resposta.getValue().at("/content/application~1json/schema/$ref").asString(""))) {
                        problemas.add(chave + " " + resposta.getKey() + ": sem o corpo de erro padrão");
                    }
                }
            }
            if (!temErro) {
                problemas.add(chave + ": sem nenhuma resposta de erro documentada");
            }
        });
        assertThat(problemas).isEmpty();
        assertThat(tagsUsadas).containsExactlyInAnyOrderElementsOf(TAGS);
    }

    @Test
    void cadeadoDoBearerSoNasRotasProtegidasConformeOPerfilAnotado() {
        Map<String, Perfil> perfis = perfisPorOperacao();
        assertThat(perfis).as("toda rota da API com @PerfilExigido").doesNotContainValue(null);
        List<String> problemas = new ArrayList<>();
        Map<String, JsonNode> operacoes = operacoes();
        perfis.forEach((chave, perfil) -> {
            JsonNode operacao = operacoes.get(chave);
            if (operacao == null) {
                problemas.add(chave + ": rota mapeada mas fora do OpenAPI");
                return;
            }
            JsonNode seguranca = operacao.path("security");
            boolean comCadeado = seguranca.size() == 1 && seguranca.get(0).has("bearerAuth");
            boolean semCadeado = seguranca.isArray() && seguranca.isEmpty();
            if (perfil.exigeToken() ? !comCadeado : !semCadeado) {
                problemas.add(chave + ": perfil " + perfil + " com security " + seguranca);
            }
            if (perfil.exigeToken() && !(operacao.path("responses").has("401") && operacao.path("responses").has("403"))) {
                problemas.add(chave + ": rota protegida sem 401 e 403 documentados");
            }
        });
        assertThat(problemas).isEmpty();
        assertThat(operacoes.get("GET /actuator/health").path("security").isEmpty()).isTrue();
    }

    @Test
    void perfilAnotadoBateComOSecurityConfigEmTodasAsRotas() throws Exception {
        String tokenCuidador = tokens.generateToken(ID_INEXISTENTE, Role.CUIDADOR);
        String tokenPaciente = tokens.generateToken(ID_INEXISTENTE, Role.PACIENTE);
        List<String> problemas = new ArrayList<>();
        for (Map.Entry<String, Perfil> rota : perfisPorOperacao().entrySet()) {
            String[] partes = rota.getKey().split(" ", 2);
            HttpMethod metodo = HttpMethod.valueOf(partes[0]);
            String caminho = partes[1].replaceAll("\\{[^}]+}", "1");
            Perfil perfil = rota.getValue();

            MockHttpServletResponse semToken = executar(metodo, caminho, null);
            if (perfil.exigeToken()) {
                exigir(problemas, rota.getKey() + " sem token", semToken, 401, "NAO_AUTENTICADO");
            } else if (semToken.getStatus() == 401 && codigo(semToken).equals("NAO_AUTENTICADO")
                    || semToken.getStatus() == 403) {
                problemas.add(rota.getKey() + ": anotada como pública, mas barrada sem token (" + semToken.getStatus() + ")");
            }

            if (perfil == Perfil.CUIDADOR || perfil == Perfil.CUIDADOR_OU_PACIENTE) {
                naoBarrada(problemas, rota.getKey() + " como CUIDADOR", executar(metodo, caminho, tokenCuidador));
            }
            if (perfil == Perfil.PACIENTE || perfil == Perfil.CUIDADOR_OU_PACIENTE) {
                naoBarrada(problemas, rota.getKey() + " como PACIENTE", executar(metodo, caminho, tokenPaciente));
            }
            if (perfil == Perfil.CUIDADOR) {
                exigir(problemas, rota.getKey() + " como PACIENTE", executar(metodo, caminho, tokenPaciente), 403,
                        "ACESSO_NEGADO");
            }
            if (perfil == Perfil.PACIENTE) {
                exigir(problemas, rota.getKey() + " como CUIDADOR", executar(metodo, caminho, tokenCuidador), 403,
                        "ACESSO_NEGADO");
            }
        }
        assertThat(problemas).isEmpty();
    }

    @Test
    void codigosDeErroPublicadosSaoOsDoCodigoErroETodosTemExemplo() {
        Set<String> codigos = new TreeSet<>(Arrays.stream(CodigoErro.values()).map(Enum::name).toList());
        Set<String> publicados = new TreeSet<>();
        contrato.at("/components/schemas/ErroResponse/properties/codigo/enum").forEach(n -> publicados.add(n.asString()));
        assertThat(publicados).isEqualTo(codigos);
        assertThat(new TreeSet<>(DocumentacaoPadraoCustomizer.codigosComExemplo())).isEqualTo(codigos);
    }

    @Test
    void limiteDeTentativasDocumentaORetryAfter() {
        JsonNode resposta = contrato.at("/paths/~1api~1vinculo~1validar/post/responses/429");
        assertThat(resposta.at("/headers/Retry-After").isMissingNode()).isFalse();
        assertThat(resposta.at("/content/application~1json/examples/LIMITE_TENTATIVAS").isMissingNode()).isFalse();
    }

    @Test
    void arquivoExportadoEstaIgualAoContratoGerado() throws Exception {
        String gerado = exportavel();
        if (Boolean.getBoolean("openapi.atualizar")) {
            Files.createDirectories(ARQUIVO_EXPORTADO.getParent());
            Files.writeString(ARQUIVO_EXPORTADO, gerado, StandardCharsets.UTF_8);
        }
        assertThat(Files.exists(ARQUIVO_EXPORTADO))
                .as("docs/api/openapi.json não existe; gere com -Dopenapi.atualizar=true").isTrue();
        // Para comparar quando divergir: target/openapi-gerado.json
        Files.writeString(Path.of("target", "openapi-gerado.json"), gerado, StandardCharsets.UTF_8);
        assertThat(Files.readString(ARQUIVO_EXPORTADO, StandardCharsets.UTF_8).replace("\r\n", "\n"))
                .as("docs/api/openapi.json desatualizado; gere de novo com -Dopenapi.atualizar=true")
                .isEqualTo(gerado);
    }

    // ---- apoio --------------------------------------------------------------------------------------------------

    /** Contrato sem a lista de servidores (o do teste é http://localhost), formatado e com quebra de linha final. */
    private String exportavel() {
        ObjectNode copia = (ObjectNode) contrato.deepCopy();
        copia.remove("servers");
        return json.writerWithDefaultPrettyPrinter().writeValueAsString(copia).replace("\r\n", "\n") + "\n";
    }

    /** "MÉTODO /caminho" -> operação, de todas as rotas publicadas. */
    private Map<String, JsonNode> operacoes() {
        Map<String, JsonNode> operacoes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> caminho : contrato.path("paths").properties()) {
            for (Map.Entry<String, JsonNode> operacao : caminho.getValue().properties()) {
                operacoes.put(operacao.getKey().toUpperCase() + " " + caminho.getKey(), operacao.getValue());
            }
        }
        return operacoes;
    }

    /** "MÉTODO /caminho" -> perfil anotado, de toda rota dos controllers da aplicação (null se faltar a anotação). */
    private Map<String, Perfil> perfisPorOperacao() {
        Map<String, Perfil> perfis = new LinkedHashMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entrada : mapeamentos.getHandlerMethods().entrySet()) {
            HandlerMethod handler = entrada.getValue();
            if (!handler.getBeanType().getPackageName().startsWith("com.medcare.senior.presentation")) {
                continue;
            }
            PerfilExigido anotacao = handler.getMethodAnnotation(PerfilExigido.class);
            for (String caminho : entrada.getKey().getPathPatternsCondition().getPatternValues()) {
                entrada.getKey().getMethodsCondition().getMethods().forEach(metodo ->
                        perfis.put(metodo.name() + " " + caminho, anotacao == null ? null : anotacao.value()));
            }
        }
        return perfis;
    }

    private MockHttpServletResponse executar(HttpMethod metodo, String caminho, String token) throws Exception {
        MockHttpServletRequestBuilder requisicao = request(metodo, caminho)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}");
        if (token != null) {
            requisicao.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(requisicao).andReturn().getResponse();
    }

    private String codigo(MockHttpServletResponse resposta) throws Exception {
        String corpo = resposta.getContentAsString(StandardCharsets.UTF_8);
        return corpo.isBlank() ? "" : json.readTree(corpo).path("codigo").asString("");
    }

    private void exigir(List<String> problemas, String caso, MockHttpServletResponse resposta, int status, String codigo)
            throws Exception {
        if (resposta.getStatus() != status || !codigo(resposta).equals(codigo)) {
            problemas.add(caso + ": esperado " + status + " " + codigo + ", veio " + resposta.getStatus() + " "
                    + codigo(resposta));
        }
    }

    /** Passou pela segurança: qualquer resposta do controller, menos a recusa do filtro (401 sem token, 403). */
    private void naoBarrada(List<String> problemas, String caso, MockHttpServletResponse resposta) throws Exception {
        if (resposta.getStatus() == 403 || codigo(resposta).equals("NAO_AUTENTICADO")) {
            problemas.add(caso + ": barrada pela segurança (" + resposta.getStatus() + " " + codigo(resposta) + ")");
        }
    }

    private static String texto(JsonNode operacao, String campo) {
        return operacao.path(campo).asString("");
    }
}
