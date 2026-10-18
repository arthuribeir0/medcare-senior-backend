package com.medcare.senior.infrastructure.notificacao;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FirebaseMessaging mockado: nada sai para o Firebase real.
 */
@ExtendWith(MockitoExtension.class)
class FCMServiceTest {

    private static final String TOKEN = "token-do-aparelho";

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private static FirebaseMessagingException erro(MessagingErrorCode codigo) {
        FirebaseMessagingException excecao = mock(FirebaseMessagingException.class);
        when(excecao.getMessagingErrorCode()).thenReturn(codigo);
        return excecao;
    }

    @Test
    void enviaParaOTokenComTituloCorpoDadosEPrioridadeAltaSemDryRunPorPadrao() throws Exception {
        FCMService service = new FCMService(firebaseMessaging, false);
        when(firebaseMessaging.send(any(Message.class), anyBoolean())).thenReturn("projects/x/messages/1");

        ResultadoEnvioPush resultado = service.enviar(TOKEN, "Dose não confirmada", "Maria ainda não confirmou a dose das 08:00",
                Map.of("tipo", "DOSE_NAO_CONFIRMADA", "doseId", "10"));

        assertThat(resultado).isEqualTo(ResultadoEnvioPush.ENVIADO);
        ArgumentCaptor<Message> mensagem = ArgumentCaptor.forClass(Message.class);
        verify(firebaseMessaging).send(mensagem.capture(), eq(false));
        // Message não expõe getters públicos; o toString do builder não é estável. Confere-se pelos campos
        // serializados que o SDK usa no envio.
        assertThat(mensagem.getValue()).hasFieldOrPropertyWithValue("token", TOKEN);
        assertThat(mensagem.getValue()).extracting("notification")
                .hasFieldOrPropertyWithValue("title", "Dose não confirmada")
                .hasFieldOrPropertyWithValue("body", "Maria ainda não confirmou a dose das 08:00");
        assertThat(mensagem.getValue()).hasFieldOrPropertyWithValue("data", Map.of("tipo", "DOSE_NAO_CONFIRMADA", "doseId", "10"));
        // Android: prioridade alta (entrega imediata mesmo em economia de bateria) e o canal "alertas-dose" criado pelo app
        assertThat(mensagem.getValue()).extracting("androidConfig").hasFieldOrPropertyWithValue("priority", "high");
        assertThat(mensagem.getValue()).extracting("androidConfig.notification")
                .hasFieldOrPropertyWithValue("channelId", FCMService.CANAL_ANDROID);
    }

    @Test
    void comDryRunEnviaSomenteParaValidacao() throws Exception {
        FCMService service = new FCMService(firebaseMessaging, true);
        when(firebaseMessaging.send(any(Message.class), anyBoolean())).thenReturn("projects/x/messages/fake");

        assertThat(service.enviar(TOKEN, "t", "c", Map.of())).isEqualTo(ResultadoEnvioPush.ENVIADO);

        verify(firebaseMessaging).send(any(Message.class), eq(true));
    }

    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode.class, names = {"UNREGISTERED", "SENDER_ID_MISMATCH", "INVALID_ARGUMENT"})
    void tokenNaoRegistradoOuInvalidoEhClassificadoComoTokenInvalido(MessagingErrorCode codigo) throws Exception {
        FCMService service = new FCMService(firebaseMessaging, false);
        FirebaseMessagingException excecao = erro(codigo);
        when(firebaseMessaging.send(any(Message.class), anyBoolean())).thenThrow(excecao);

        assertThat(service.enviar(TOKEN, "t", "c", Map.of())).isEqualTo(ResultadoEnvioPush.TOKEN_INVALIDO);
    }

    @ParameterizedTest
    @EnumSource(value = MessagingErrorCode.class, names = {"UNAVAILABLE", "INTERNAL", "QUOTA_EXCEEDED",
            "THIRD_PARTY_AUTH_ERROR"})
    void erroDoProvedorEhClassificadoComoFalhaTemporaria(MessagingErrorCode codigo) throws Exception {
        FCMService service = new FCMService(firebaseMessaging, false);
        FirebaseMessagingException excecao = erro(codigo);
        when(firebaseMessaging.send(any(Message.class), anyBoolean())).thenThrow(excecao);

        assertThat(service.enviar(TOKEN, "t", "c", Map.of())).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
    }

    @Test
    void erroSemCodigoDeMensageriaEhFalhaTemporaria() throws Exception {
        FCMService service = new FCMService(firebaseMessaging, false);
        FirebaseMessagingException excecao = erro(null);
        when(firebaseMessaging.send(any(Message.class), anyBoolean())).thenThrow(excecao);

        assertThat(service.enviar(TOKEN, "t", "c", Map.of())).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
    }

    @Test
    void erroInesperadoNaoPropagaEhFalhaTemporaria() throws Exception {
        FCMService service = new FCMService(firebaseMessaging, false);
        when(firebaseMessaging.send(any(Message.class), anyBoolean())).thenThrow(new IllegalStateException("rede"));

        assertThat(service.enviar(TOKEN, "t", "c", Map.of())).isEqualTo(ResultadoEnvioPush.FALHA_TEMPORARIA);
    }

    @Test
    void todoCodigoDoSdkTemClassificacao() {
        for (MessagingErrorCode codigo : MessagingErrorCode.values()) {
            assertThat(FCMService.classificar(codigo)).isNotNull();
        }
    }
}
