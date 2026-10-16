package com.medcare.senior.infrastructure.notificacao;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Inicializa o Firebase Admin SDK para envio de push (FCM). Só existe com app.fcm.enabled=true (FCM_ENABLED); o
 * padrão, os testes e o CI rodam com false, e aí nenhum bean do Firebase é criado.
 *
 * <p>O arquivo de credenciais (conta de serviço do Firebase, JSON) é lido do caminho em app.fcm.credentials-path
 * (FIREBASE_CREDENTIALS_PATH). Ele NUNCA fica no repositório: o .gitignore bloqueia *firebase*.json e
 * *service-account*.json. Habilitado sem arquivo válido, a aplicação não sobe (fail fast), em vez de subir e
 * perder avisos em silêncio.
 */
@Configuration
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class FCMInitializer {

    // Nome próprio (e não o DEFAULT) para não colidir com outra inicialização do Firebase no mesmo processo
    static final String NOME_APP = "medcare-senior";

    private static final Logger log = LoggerFactory.getLogger(FCMInitializer.class);

    // destroyMethod: ao fechar o contexto (ex.: testes), libera o nome para uma nova inicialização
    @Bean(destroyMethod = "delete")
    public FirebaseApp firebaseApp(@Value("${app.fcm.credentials-path:}") String credentialsPath) {
        Path arquivo = validarArquivo(credentialsPath);
        try (InputStream credenciais = Files.newInputStream(arquivo)) {
            GoogleCredentials credentials = GoogleCredentials.fromStream(credenciais);
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp app = FirebaseApp.initializeApp(options, NOME_APP);
            // Só o ID do projeto e o nome do arquivo: nunca o conteúdo da credencial
            log.info("Firebase Admin SDK inicializado (app '{}', projeto '{}', credencial '{}')", NOME_APP,
                    credentials instanceof ServiceAccountCredentials conta ? conta.getProjectId() : "desconhecido",
                    arquivo.getFileName());
            return app;
        } catch (IOException e) {
            // A mensagem da causa não é repassada: pode trazer trechos do arquivo de credenciais
            throw new IllegalStateException("FCM habilitado (app.fcm.enabled=true), mas o arquivo de credenciais em "
                    + arquivo + " não pôde ser lido como conta de serviço do Firebase. Verifique o JSON baixado "
                    + "no console do Firebase (Contas de serviço > Gerar nova chave privada).");
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }

    private static Path validarArquivo(String credentialsPath) {
        if (credentialsPath == null || credentialsPath.isBlank()) {
            throw new IllegalStateException("FCM habilitado (app.fcm.enabled=true), mas a variável de ambiente "
                    + "FIREBASE_CREDENTIALS_PATH (app.fcm.credentials-path) não foi definida. Informe o caminho do "
                    + "arquivo JSON da conta de serviço do Firebase, ou desligue o FCM com FCM_ENABLED=false.");
        }
        Path arquivo = Path.of(credentialsPath.trim());
        if (!Files.isRegularFile(arquivo) || !Files.isReadable(arquivo)) {
            throw new IllegalStateException("FCM habilitado (app.fcm.enabled=true), mas o arquivo de credenciais "
                    + "do Firebase não existe ou não pode ser lido: " + arquivo.toAbsolutePath()
                    + ". Confira FIREBASE_CREDENTIALS_PATH, ou desligue o FCM com FCM_ENABLED=false.");
        }
        return arquivo;
    }
}
