package com.medcare.senior.infrastructure.config;

import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Parâmetros da verificação de doses vencidas (app.alertas.*). Valores inválidos (negativos, lote zero) impedem a
 * aplicação de subir.
 */
@Configuration
public class AlertasConfig {

    @Bean
    public VerificarDosesVencidasUseCase.Parametros parametrosDeAlerta(
            @Value("${app.alertas.tolerancia-minutos:30}") long toleranciaMinutos,
            @Value("${app.alertas.janela-notificacao-horas:24}") long janelaNotificacaoHoras,
            @Value("${app.alertas.lote-maximo:500}") int loteMaximo) {
        return new VerificarDosesVencidasUseCase.Parametros(
                Duration.ofMinutes(toleranciaMinutos), Duration.ofHours(janelaNotificacaoHoras), loteMaximo);
    }
}
