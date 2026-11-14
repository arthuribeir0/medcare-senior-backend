package com.medcare.senior.presentation;

import com.medcare.senior.domain.email.EnviadorDeEmailPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.email.VerificadorDeDominioPort;
import com.medcare.senior.domain.usuario.Role;
import com.medcare.senior.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirmação do e-mail e troca de senha pela API, contra o PostgreSQL real: cadastro com código, bloqueio do cadastro de
 * paciente, confirmação, reenvio, "esqueci minha senha" e a senha nova. O envio é trocado por um que guarda o último
 * código de cada e-mail. Cada teste é desfeito com rollback.
 */
@SpringBootTest(properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@Transactional
class VerificacaoDeEmailApiTest {

    private static final String SENHA = "SenhaForte1";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtTokenProvider tokens;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private CaixaDeEntrada caixa;

    private String email;

    @TestConfiguration
    static class Configuracao {
        @Bean
        CaixaDeEntrada caixaDeEntrada() {
            return new CaixaDeEntrada();
        }

        @Bean
        @Primary
        EnviadorDeEmailPort enviadorDeTeste(CaixaDeEntrada caixa) {
            return (destinatario, nome, finalidade, codigo) -> {
                caixa.codigos.put(destinatario + "|" + finalidade, codigo);
                return true;
            };
        }

        // "naoexiste.invalid" não existe; o resto, sim (sem consultar o DNS de verdade)
        @Bean
        @Primary
        VerificadorDeDominioPort verificadorDeTeste() {
            return dominio -> !dominio.endsWith(".invalid");
        }
    }

    static class CaixaDeEntrada {
        final Map<String, String> codigos = new ConcurrentHashMap<>();

        String ultimo(String email, FinalidadeDoCodigo finalidade) {
            return codigos.get(email + "|" + finalidade);
        }
    }

    @BeforeEach
    void setUp() {
        email = "ana-" + UUID.randomUUID() + "@exemplo.com";
    }

    private ResultActions cadastrar(String emailDoCadastro) throws Exception {
        return mvc.perform(post("/api/cuidadores").contentType(MediaType.APPLICATION_JSON).content("""
                {"nome": "Ana Souza", "email": "%s", "senha": "%s", "consentimentoLgpd": true}
                """.formatted(emailDoCadastro, SENHA)));
    }

    private Long idDoCadastro() throws Exception {
        String corpo = cadastrar(email).andExpect(status().isCreated())
                .andExpect(jsonPath("$.emailVerificado").value(false))
                .andReturn().getResponse().getContentAsString();
        return Long.valueOf(corpo.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String bearer(Long cuidadorId) {
        return "Bearer " + tokens.generateToken(cuidadorId, Role.CUIDADOR);
    }

    private ResultActions confirmar(Long cuidadorId, String codigo) throws Exception {
        return mvc.perform(post("/api/cuidadores/me/verificacao-email/confirmar")
                .header(HttpHeaders.AUTHORIZATION, bearer(cuidadorId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"codigo\": \"" + codigo + "\"}"));
    }

    private ResultActions cadastrarPaciente(Long cuidadorId) throws Exception {
        return mvc.perform(post("/api/pacientes").header(HttpHeaders.AUTHORIZATION, bearer(cuidadorId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nome\": \"Maria\", \"dataNascimento\": \"1945-03-12\"}"));
    }

    private ResultActions login(String senha) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\", \"senha\": \"" + senha + "\"}"));
    }

    @Test
    void cadastroEnviaOCodigoEOPacienteSoDepoisDeConfirmar() throws Exception {
        Long id = idDoCadastro();
        String codigo = caixa.ultimo(email, FinalidadeDoCodigo.VERIFICACAO);
        assertThat(codigo).matches("\\d{6}");
        // O banco guarda só o hash
        assertThat(jdbc.queryForObject("SELECT codigo_hash FROM codigo_email WHERE cuidador_id = ?", String.class, id))
                .hasSize(64).isNotEqualTo(codigo);

        login(SENHA).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerificado").value(false));
        cadastrarPaciente(id).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("EMAIL_NAO_VERIFICADO"));

        String errado = codigo.equals("000000") ? "111111" : "000000";
        confirmar(id, errado).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_EMAIL_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Código incorreto. Você tem mais 4 tentativas."));

        confirmar(id, codigo).andExpect(status().isOk()).andExpect(jsonPath("$.emailVerificado").value(true));
        login(SENHA).andExpect(jsonPath("$.emailVerificado").value(true));
        mvc.perform(get("/api/cuidadores/me").header(HttpHeaders.AUTHORIZATION, bearer(id)))
                .andExpect(jsonPath("$.emailVerificado").value(true));
        cadastrarPaciente(id).andExpect(status().isCreated());
        // Confirmar de novo não muda nada
        confirmar(id, "999999").andExpect(status().isOk());
    }

    @Test
    void reenviarSoDepoisDeUmMinuto() throws Exception {
        Long id = idDoCadastro();

        mvc.perform(post("/api/cuidadores/me/verificacao-email/reenviar").header(HttpHeaders.AUTHORIZATION, bearer(id)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("LIMITE_TENTATIVAS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));

        jdbc.update("UPDATE codigo_email SET criado_em = criado_em - INTERVAL '2 minutes' WHERE cuidador_id = ?", id);
        String anterior = caixa.ultimo(email, FinalidadeDoCodigo.VERIFICACAO);
        mvc.perform(post("/api/cuidadores/me/verificacao-email/reenviar").header(HttpHeaders.AUTHORIZATION, bearer(id)))
                .andExpect(status().isNoContent());
        String novo = caixa.ultimo(email, FinalidadeDoCodigo.VERIFICACAO);
        if (!novo.equals(anterior)) {
            confirmar(id, anterior).andExpect(status().isBadRequest());
        }
        confirmar(id, novo).andExpect(status().isOk());
    }

    @Test
    void cadastroRecusaEmailForaDoFormatoOuDeDominioInexistente() throws Exception {
        cadastrar("ana@b").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("VALIDACAO"));
        cadastrar("ana@naoexiste.invalid").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("EMAIL_INVALIDO"))
                .andExpect(jsonPath("$.campos[0].campo").value("email"))
                .andExpect(jsonPath("$.mensagem", containsString("naoexiste.invalid")));
    }

    @Test
    void esqueciASenhaTrocaASenhaEConfirmaOEmail() throws Exception {
        idDoCadastro();

        mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email.toUpperCase() + "\"}"))
                .andExpect(status().isNoContent());
        String codigo = caixa.ultimo(email, FinalidadeDoCodigo.REDEFINICAO_SENHA);
        assertThat(codigo).matches("\\d{6}");

        redefinir(email, codigo, "fraca").andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("SENHA_FRACA"));
        redefinir(email, codigo, "SenhaNova2").andExpect(status().isNoContent());

        login(SENHA).andExpect(status().isUnauthorized());
        login("SenhaNova2").andExpect(status().isOk()).andExpect(jsonPath("$.emailVerificado").value(true));
        // O código já foi usado
        redefinir(email, codigo, "OutraSenha3").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_EMAIL_INVALIDO"));
    }

    @Test
    void esqueciASenhaDeEmailSemContaRespondeIgualENaoEnviaNada() throws Exception {
        String semConta = "ninguem-" + UUID.randomUUID() + "@exemplo.com";
        mvc.perform(post("/api/auth/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + semConta + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(caixa.ultimo(semConta, FinalidadeDoCodigo.REDEFINICAO_SENHA)).isNull();

        redefinir(semConta, "123456", "SenhaNova2").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("CODIGO_EMAIL_INVALIDO"))
                .andExpect(jsonPath("$.mensagem").value("Este código não vale mais. Peça um novo código."));
    }

    @Test
    void rotasDoEmailExigemOPerfilCerto() throws Exception {
        mvc.perform(post("/api/cuidadores/me/verificacao-email/reenviar")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/cuidadores/me/verificacao-email/reenviar")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.generateToken(1L, Role.PACIENTE)))
                .andExpect(status().isForbidden());
    }

    private ResultActions redefinir(String emailDaConta, String codigo, String novaSenha) throws Exception {
        return mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
                .with(requisicao -> {
                    // Cada teste com o seu IP: o limite por IP de um não atrapalha o outro
                    requisicao.setRemoteAddr("10.0.0." + Math.abs(emailDaConta.hashCode() % 250));
                    return requisicao;
                })
                .content("""
                        {"email": "%s", "codigo": "%s", "novaSenha": "%s"}
                        """.formatted(emailDaConta, codigo, novaSenha)));
    }
}
