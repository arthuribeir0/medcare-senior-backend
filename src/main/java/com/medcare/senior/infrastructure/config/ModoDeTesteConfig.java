package com.medcare.senior.infrastructure.config;

import com.medcare.senior.domain.configuracao.ModoDeTeste;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Modo de teste (app.modo-teste / APP_MODO_TESTE, desligado por padrão): tolerância de 1 minuto e qualquer horário no
 * tratamento, para testar o app no aparelho sem esperar. Ligado, avisa no log ao subir.
 */
@Configuration
public class ModoDeTesteConfig {

    private static final Logger log = LoggerFactory.getLogger(ModoDeTesteConfig.class);

    @Bean
    public ModoDeTeste modoDeTeste(@Value("${app.modo-teste:false}") boolean ativo) {
        if (ativo) {
            log.warn("Modo de teste LIGADO: tolerância a partir de 1 minuto e horários sem a antecedência mínima");
        }
        return ativo ? new ModoDeTeste(true) : ModoDeTeste.DESLIGADO;
    }
}
