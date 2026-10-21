package com.medcare.senior.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Só no perfil prod (SPRING_PROFILES_ACTIVE=prod): confere os segredos ANTES de criar qualquer bean (inclusive o
 * pool de conexões e o Flyway) e recusa subir com JWT_SECRET ausente, padrão ou curto, ou com a senha do banco ausente
 * ou padrão. A mensagem cita só o nome das variáveis, nunca os valores. Fora do perfil prod, nada muda.
 *
 * <p>Com o FCM desligado em produção o backend sobe, mas registra um ERRO destacado a cada inicialização: os avisos
 * de dose não confirmada não chegam ao aparelho de ninguém.
 */
@Component
@Profile("prod")
public class ValidacaoDeProducao implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final Logger log = LoggerFactory.getLogger(ValidacaoDeProducao.class);

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        List<String> problemas = SegredosDeProducao.problemas(environment.getProperty("jwt.secret"),
                environment.getProperty("spring.datasource.password"));
        if (!problemas.isEmpty()) {
            throw new IllegalStateException("Configuração de produção inválida (perfil prod): " + String.join("; ", problemas)
                    + ". Defina as variáveis no .env.prod (ver docs/implantacao.md).");
        }
        if (!environment.getProperty("app.fcm.enabled", Boolean.class, false)) {
            log.error("==================================================================================================");
            log.error("PRODUÇÃO COM FCM DESLIGADO (FCM_ENABLED=false): os avisos de dose não confirmada NÃO serão entregues "
                    + "aos cuidadores. Eles só aparecem no log e o evento de notificação fica marcado como ENVIADO.");
            log.error("==================================================================================================");
        }
    }
}
