package com.medcare.senior.presentation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IP usado pelo limite de tentativas do pareamento, com o Tomcat de verdade (porta aleatória): o MockMvc não passa
 * pela válvula do Tomcat que lê o X-Forwarded-For.
 *
 * <p>No perfil prod (atrás do nginx da VM ou do ngrok da apresentação no PC) o IP vem do X-Forwarded-For gravado pelo
 * proxy em rede interna, então dois celulares nunca dividem o limite. Fora dele o cabeçalho é ignorado: sem proxy na
 * frente, aceitá-lo deixaria qualquer cliente trocar de IP a cada tentativa.
 */
class IpDoClienteAtrasDoProxyTest {

    private static final String CODIGO_INEXISTENTE = "{\"codigo\":\"000000\"}";
    private static final int TENTATIVAS_ATE_BLOQUEAR = 5;
    private static final AtomicInteger PROXIMO_IP = new AtomicInteger(1);
    private static final HttpClient CLIENTE = HttpClient.newHttpClient();

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false",
                    "jwt.secret=segredo-do-teste-do-perfil-prod-com-mais-de-32-caracteres"})
    @ActiveProfiles("prod")
    class AtrasDoProxyNoPerfilProd {

        @LocalServerPort
        private int porta;

        @Test
        void cadaCelularTemOProprioLimitePeloIpEncaminhado() throws Exception {
            String celularA = ipDeTeste();
            String celularB = outroIp(celularA);

            esgotarTentativas(porta, celularA);

            assertThat(validar(porta, celularA)).isEqualTo(429);
            assertThat(validar(porta, celularB)).isEqualTo(400);
        }

        @Test
        void ipFalsoAcrescentadoPeloClienteNaoEscapaDoLimite() throws Exception {
            String celular = ipDeTeste();
            esgotarTentativas(porta, celular);

            // O proxy acrescenta o IP real no fim; o que o cliente mandou antes não conta
            assertThat(validar(porta, outroIp(celular) + ", " + celular)).isEqualTo(429);
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"app.jobs.enabled=false", "app.fcm.enabled=false", "spring.jpa.show-sql=false"})
    class SemProxyForaDoPerfilProd {

        @LocalServerPort
        private int porta;

        @Test
        void cabecalhoEncaminhadoEIgnoradoEOLimiteValeParaAConexao() throws Exception {
            String ip = ipDeTeste();
            esgotarTentativas(porta, ip);

            // Mesma conexão (127.0.0.1), outro X-Forwarded-For: continua bloqueado
            assertThat(validar(porta, outroIp(ip))).isEqualTo(429);
        }
    }

    private static void esgotarTentativas(int porta, String ip) throws IOException, InterruptedException {
        for (int i = 0; i < TENTATIVAS_ATE_BLOQUEAR; i++) {
            assertThat(validar(porta, ip)).isEqualTo(400);
        }
    }

    private static int validar(int porta, String xForwardedFor) throws IOException, InterruptedException {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + porta + "/api/vinculo/validar"))
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", xForwardedFor)
                .header("X-Forwarded-Proto", "https")
                .POST(HttpRequest.BodyPublishers.ofString(CODIGO_INEXISTENTE))
                .build();
        return CLIENTE.send(requisicao, HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    // Faixa de documentação (TEST-NET-3), um IP por teste para não herdar bloqueios de outro teste no mesmo contexto
    private static String ipDeTeste() {
        return "203.0.113." + PROXIMO_IP.getAndIncrement();
    }

    private static String outroIp(String ip) {
        int ultimo = Integer.parseInt(ip.substring(ip.lastIndexOf('.') + 1));
        return "198.51.100." + ultimo;
    }
}
