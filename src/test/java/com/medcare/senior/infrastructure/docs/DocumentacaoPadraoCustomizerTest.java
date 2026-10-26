package com.medcare.senior.infrastructure.docs;

import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.tags.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentacaoPadraoCustomizerTest {

    private final DocumentacaoPadraoCustomizer customizer = new DocumentacaoPadraoCustomizer();

    @SuppressWarnings("unused")
    static class Rotas {
        @PerfilExigido(Perfil.PUBLICO)
        void publica() {
        }

        @PerfilExigido(Perfil.CUIDADOR)
        void protegida() {
        }

        void semAnotacao() {
        }
    }

    private static HandlerMethod rota(String nome) throws NoSuchMethodException {
        return new HandlerMethod(new Rotas(), Rotas.class.getDeclaredMethod(nome));
    }

    @Test
    void rotaSemAnotacaoFicaComoVeio() throws Exception {
        Operation operacao = new Operation().description("Original");

        customizer.customize(operacao, rota("semAnotacao"));

        assertThat(operacao.getDescription()).isEqualTo("Original");
        assertThat(operacao.getSecurity()).isNull();
        assertThat(operacao.getResponses()).isNull();
    }

    @Test
    void rotaPublicaSemDescricaoNemRespostasGanhaPerfilSemCadeadoESo500() throws Exception {
        Operation operacao = new Operation();

        customizer.customize(operacao, rota("publica"));

        assertThat(operacao.getDescription()).isEqualTo("**Perfil exigido:** público, sem token.");
        assertThat(operacao.getSecurity()).isEmpty();
        assertThat(operacao.getResponses()).containsOnlyKeys("500");
    }

    @Test
    void rotaProtegidaGanhaCadeado401E403SemTrocarOQueJaEstavaDocumentado() throws Exception {
        Operation operacao = new Operation().description("Detalhe")
                .responses(new ApiResponses().addApiResponse("401", new ApiResponse().description("Específico")));

        customizer.customize(operacao, rota("protegida"));

        assertThat(operacao.getDescription()).isEqualTo("**Perfil exigido:** CUIDADOR.\n\nDetalhe");
        assertThat(operacao.getSecurity()).singleElement()
                .satisfies(requisito -> assertThat(requisito).containsKey(OpenApiConfig.BEARER_JWT));
        assertThat(operacao.getResponses().get("401").getDescription()).isEqualTo("Específico");
        assertThat(operacao.getResponses()).containsKeys("403", "500");
    }

    @Test
    void errosGanhamOCorpoPadraoComUmExemploPorCodigoCitadoOuOCodigoPadraoDoStatus() {
        Operation operacao = new Operation().responses(new ApiResponses()
                .addApiResponse("200", new ApiResponse().description("Ok"))
                .addApiResponse("409", new ApiResponse().description("DOSE_JA_REGISTRADA ou DOSE_ENCERRADA"))
                .addApiResponse("404", new ApiResponse().description("Não existe; CODIGO_QUE_NAO_EXISTE"))
                .addApiResponse("418", new ApiResponse())
                .addApiResponse("429", new ApiResponse().description("Muitas tentativas")));
        OpenAPI openApi = new OpenAPI().paths(new Paths()
                .addPathItem("/x", new PathItem().get(operacao).post(new Operation())));

        customizer.customise(openApi);

        ApiResponses respostas = openApi.getPaths().get("/x").getGet().getResponses();
        assertThat(respostas.get("200").getContent()).isNull();
        assertThat(exemplos(respostas.get("409"))).containsOnlyKeys("DOSE_JA_REGISTRADA", "DOSE_ENCERRADA");
        assertThat(exemplos(respostas.get("404"))).containsOnlyKeys("RECURSO_NAO_ENCONTRADO");
        assertThat(exemplos(respostas.get("418"))).isNullOrEmpty();
        assertThat(json(respostas.get("418")).getSchema().get$ref()).isEqualTo(DocumentacaoPadraoCustomizer.ERRO_RESPONSE_REF);
        assertThat(respostas.get("429").getHeaders()).containsKey("Retry-After");
        assertThat(exemplos(respostas.get("429"))).containsOnlyKeys("LIMITE_TENTATIVAS");
    }

    @Test
    void saudeEntraUmaVezSoComATagMesmoSeATagJaExistir() {
        OpenAPI semTags = new OpenAPI().paths(new Paths());
        customizer.customise(semTags);
        customizer.customise(semTags);
        assertThat(semTags.getTags()).extracting(Tag::getName).containsExactly(DocumentacaoPadraoCustomizer.TAG_SAUDE);
        assertThat(semTags.getPaths().get("/actuator/health").getGet().getSecurity()).isEmpty();

        OpenAPI comTags = new OpenAPI().paths(new Paths()).addTagsItem(new Tag().name("Outra"));
        customizer.customise(comTags);
        assertThat(comTags.getTags()).extracting(Tag::getName).containsExactly("Outra", DocumentacaoPadraoCustomizer.TAG_SAUDE);
    }

    private static MediaType json(ApiResponse resposta) {
        return resposta.getContent().get("application/json");
    }

    private static Map<String, ?> exemplos(ApiResponse resposta) {
        return json(resposta).getExamples();
    }
}
