package com.medcare.senior.infrastructure.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registra o {@link SessaoDoCuidadorInterceptor} e o {@link SessaoDoPacienteInterceptor} em todas as rotas da API.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final SessaoDoCuidadorInterceptor sessaoDoCuidadorInterceptor;
    private final SessaoDoPacienteInterceptor sessaoDoPacienteInterceptor;

    public WebMvcConfig(SessaoDoCuidadorInterceptor sessaoDoCuidadorInterceptor,
                        SessaoDoPacienteInterceptor sessaoDoPacienteInterceptor) {
        this.sessaoDoCuidadorInterceptor = sessaoDoCuidadorInterceptor;
        this.sessaoDoPacienteInterceptor = sessaoDoPacienteInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessaoDoCuidadorInterceptor).addPathPatterns("/api/**");
        registry.addInterceptor(sessaoDoPacienteInterceptor).addPathPatterns("/api/**");
    }
}
