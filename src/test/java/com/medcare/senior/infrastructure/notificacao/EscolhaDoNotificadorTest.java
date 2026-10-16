package com.medcare.senior.infrastructure.notificacao;

import com.medcare.senior.domain.notificacao.NotificacaoPushPort;
import com.medcare.senior.domain.notificacao.NotificadorDeDoseNaoTomadaPort;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Qual implementação do aviso de dose não tomada é usada, conforme app.fcm.enabled. Sempre exatamente uma.
 */
class EscolhaDoNotificadorTest {

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(CuidadorRepositoryPort.class, () -> mock(CuidadorRepositoryPort.class))
            .withBean(NotificacaoPushPort.class, () -> mock(NotificacaoPushPort.class))
            .withBean(Clock.class, Clock::systemDefaultZone)
            .withUserConfiguration(LogNotificadorDeDoseNaoTomadaAdapter.class,
                    PushNotificadorDeDoseNaoTomadaAdapter.class);

    @Test
    void semAPropriedadeUsaALogImplementacao() {
        contexto.run(ctx -> {
            assertThat(ctx).hasSingleBean(NotificadorDeDoseNaoTomadaPort.class);
            assertThat(ctx).getBean(NotificadorDeDoseNaoTomadaPort.class)
                    .isInstanceOf(LogNotificadorDeDoseNaoTomadaAdapter.class);
        });
    }

    @Test
    void comFcmDesligadoUsaALogImplementacao() {
        contexto.withPropertyValues("app.fcm.enabled=false").run(ctx ->
                assertThat(ctx).getBean(NotificadorDeDoseNaoTomadaPort.class)
                        .isInstanceOf(LogNotificadorDeDoseNaoTomadaAdapter.class));
    }

    @Test
    void comFcmLigadoUsaOPush() {
        contexto.withPropertyValues("app.fcm.enabled=true").run(ctx -> {
            assertThat(ctx).hasSingleBean(NotificadorDeDoseNaoTomadaPort.class);
            assertThat(ctx).getBean(NotificadorDeDoseNaoTomadaPort.class)
                    .isInstanceOf(PushNotificadorDeDoseNaoTomadaAdapter.class);
        });
    }
}
