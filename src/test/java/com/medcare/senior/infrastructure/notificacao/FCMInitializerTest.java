package com.medcare.senior.infrastructure.notificacao;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inicialização condicional do Firebase, só com a configuração (sem banco nem aplicação inteira). Nenhum teste
 * fala com o Firebase: inicializar o FirebaseApp não acessa a rede.
 */
class FCMInitializerTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withUserConfiguration(FCMInitializer.class);

    @Test
    void semAPropriedadeNenhumBeanDoFirebaseEhCriado() {
        contexto.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx).doesNotHaveBean(FirebaseApp.class);
            assertThat(ctx).doesNotHaveBean(FirebaseMessaging.class);
        });
    }

    @Test
    void comFcmDesligadoNenhumBeanDoFirebaseEhCriadoMesmoComCaminho() {
        contexto.withPropertyValues("app.fcm.enabled=false", "app.fcm.credentials-path=/nao/existe.json")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).doesNotHaveBean(FirebaseApp.class);
                });
    }

    @Test
    void habilitadoSemCaminhoFalhaComMensagemClara() {
        contexto.withPropertyValues("app.fcm.enabled=true")
                .run(ctx -> assertThat(ctx).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("FIREBASE_CREDENTIALS_PATH")
                        .hasMessageContaining("não foi definida"));
    }

    @Test
    void habilitadoComArquivoInexistenteFalhaComMensagemClara(@TempDir Path pasta) {
        Path inexistente = pasta.resolve("nao-existe.json");

        contexto.withPropertyValues("app.fcm.enabled=true", "app.fcm.credentials-path=" + inexistente)
                .run(ctx -> assertThat(ctx).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("não existe ou não pode ser lido")
                        .hasMessageContaining("nao-existe.json"));
    }

    @Test
    void habilitadoComArquivoInvalidoFalhaSemRepassarOConteudo(@TempDir Path pasta) throws Exception {
        Path invalido = Files.writeString(pasta.resolve("credenciais.json"), "{\"type\": \"conteudo-secreto\"}");

        contexto.withPropertyValues("app.fcm.enabled=true", "app.fcm.credentials-path=" + invalido)
                .run(ctx -> assertThat(ctx).getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("não pôde ser lido como conta de serviço")
                        .hasMessageNotContaining("conteudo-secreto"));
    }

    @Test
    void habilitadoComCredencialValidaCriaFirebaseAppEMessaging(@TempDir Path pasta) throws Exception {
        Path credencial = Files.writeString(pasta.resolve("credenciais.json"), contaDeServicoFalsa());

        contexto.withPropertyValues("app.fcm.enabled=true", "app.fcm.credentials-path=" + credencial)
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(FirebaseApp.class);
                    assertThat(ctx).hasSingleBean(FirebaseMessaging.class);
                    assertThat(ctx.getBean(FirebaseApp.class).getName()).isEqualTo(FCMInitializer.NOME_APP);
                });

        // Fechar o contexto apaga o FirebaseApp (destroyMethod), liberando o nome
        assertThat(FirebaseApp.getApps()).noneMatch(app -> app.getName().equals(FCMInitializer.NOME_APP));
    }

    /**
     * JSON no formato de conta de serviço, com uma chave RSA gerada na hora: não é credencial de nenhum projeto real.
     */
    private static String contaDeServicoFalsa() throws Exception {
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);
        String chave = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(gerador.generateKeyPair().getPrivate().getEncoded());
        String pem = "-----BEGIN PRIVATE KEY-----\n" + chave + "\n-----END PRIVATE KEY-----\n";
        return """
                {
                  "type": "service_account",
                  "project_id": "medcare-teste",
                  "private_key_id": "teste",
                  "private_key": "%s",
                  "client_email": "teste@medcare-teste.iam.gserviceaccount.com",
                  "client_id": "1",
                  "token_uri": "https://oauth2.googleapis.com/token"
                }
                """.formatted(pem.replace("\n", "\\n"));
    }
}
