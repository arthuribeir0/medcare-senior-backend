package com.medcare.senior.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra o {@link SessaoDoCuidadorInterceptor} em todas as rotas da API.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessaoDoCuidadorInterceptor sessaoDoCuidadorInterceptor;

    public WebMvcConfig(SessaoDoCuidadorInterceptor sessaoDoCuidadorInterceptor) {
        this.sessaoDoCuidadorInterceptor = sessaoDoCuidadorInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessaoDoCuidadorInterceptor).addPathPatterns("/api/**");
    }
}
