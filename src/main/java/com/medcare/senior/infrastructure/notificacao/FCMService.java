package com.medcare.senior.infrastructure.notificacao;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Envio de push pelo Firebase Cloud Messaging (UC-007). Só existe com app.fcm.enabled=true.
 *
 * <p>app.fcm.dry-run=true envia com validateOnly: o Firebase valida credencial, token e mensagem, mas não entrega
 * nada ao aparelho. Útil para testar a configuração em homologação.
 *
 * <p>LGPD: o token nunca vai para o log. Também não são registradas a mensagem nem a pilha da exceção do Firebase,
 * que podem conter trechos da requisição; só os códigos de erro.
 */
@Component
@ConditionalOnProperty(name = "app.fcm.enabled", havingValue = "true")
public class FCMService implements NotificacaoPushPort {

    private static final Logger log = LoggerFactory.getLogger(FCMService.class);

    private final FirebaseMessaging firebaseMessaging;
    private final boolean dryRun;

    public FCMService(FirebaseMessaging firebaseMessaging, @Value("${app.fcm.dry-run:false}") boolean dryRun) {
        this.firebaseMessaging = firebaseMessaging;
        this.dryRun = dryRun;
        if (dryRun) {
            log.warn("Push pelo FCM ativo em DRY-RUN (app.fcm.dry-run=true): o Firebase valida as mensagens, mas "
                    + "NÃO as entrega aos aparelhos");
        } else {
            log.info("Push pelo FCM ativo: avisos de dose não confirmada são entregues aos aparelhos dos cuidadores");
        }
    }

    /**
     * Canal de notificação do Android criado pelo app (importância alta, "Alertas de dose"). Se o app ainda não o
     * criou, o Android usa o canal padrão.
     */
    static final String CANAL_ANDROID = "alertas-dose";

    @Override
    public ResultadoEnvioPush enviar(String tokenDestino, String titulo, String corpo, Map<String, String> dados) {
        Message mensagem = Message.builder()
                .setToken(tokenDestino)
                .setNotification(Notification.builder().setTitle(titulo).setBody(corpo).build())
                .putAllData(dados == null ? Map.of() : dados)
                // Prioridade alta: entrega imediata mesmo com o aparelho em economia de bateria (Doze)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setNotification(AndroidNotification.builder().setChannelId(CANAL_ANDROID).build())
                        .build())
                .build();
        try {
            firebaseMessaging.send(mensagem, dryRun);
            log.debug("Push aceito pelo FCM (dryRun={})", dryRun);
            return ResultadoEnvioPush.ENVIADO;
        } catch (FirebaseMessagingException e) {
            ResultadoEnvioPush resultado = classificar(e.getMessagingErrorCode());
            log.warn("Push recusado pelo FCM: messagingErrorCode={}, errorCode={}, resultado={}",
                    e.getMessagingErrorCode(), e.getErrorCode(), resultado);
            return resultado;
        } catch (RuntimeException e) {
            log.warn("Falha inesperada ao enviar push pelo FCM: {}", e.getClass().getName());
            return ResultadoEnvioPush.FALHA_TEMPORARIA;
        }
    }

    /**
     * UNREGISTERED: app desinstalado ou token expirado. SENDER_ID_MISMATCH: token de outro projeto Firebase.
     * INVALID_ARGUMENT: na prática, token malformado (o título e o corpo são montados aqui e sempre válidos).
     * Todo o resto (UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED, THIRD_PARTY_AUTH_ERROR, erro sem código) é tratado
     * como temporário: não é culpa do token, e pode funcionar mais tarde.
     */
    static ResultadoEnvioPush classificar(MessagingErrorCode codigo) {
        if (codigo == null) {
            return ResultadoEnvioPush.FALHA_TEMPORARIA;
        }
        return switch (codigo) {
            case UNREGISTERED, SENDER_ID_MISMATCH, INVALID_ARGUMENT -> ResultadoEnvioPush.TOKEN_INVALIDO;
            default -> ResultadoEnvioPush.FALHA_TEMPORARIA;
        };
    }
}
