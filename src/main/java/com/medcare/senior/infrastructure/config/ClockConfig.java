package com.medcare.senior.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Relógio único da aplicação, no fuso de app.timezone (padrão America/Sao_Paulo). Serviços que dependem da
 * data/hora atual recebem este Clock, e não chamam LocalDate.now() direto, para os testes serem determinísticos
 * e para "hoje" e "agora" não dependerem do fuso do servidor.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
