package com.medcare.senior.infrastructure.config;

import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Parâmetros da verificação de doses vencidas e da janela de confirmação (app.alertas.*). Valores inválidos
 * (negativos, zero, lote zero, tolerância maior ou igual ao encerramento) impedem a aplicação de subir.
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

    /**
     * Janela de confirmação de dose. O construtor de PoliticaDeJanela valida: todos positivos e tolerância menor que
     * o encerramento.
     */
    @Bean
    public PoliticaDeJanela politicaDeJanela(
            @Value("${app.alertas.antecedencia-confirmacao-minutos:60}") long antecedenciaMinutos,
            @Value("${app.alertas.tolerancia-minutos:30}") long toleranciaMinutos,
            @Value("${app.alertas.encerramento-minutos:180}") long encerramentoMinutos) {
        return new PoliticaDeJanela(Duration.ofMinutes(antecedenciaMinutos), Duration.ofMinutes(toleranciaMinutos),
                Duration.ofMinutes(encerramentoMinutos));
    }
}
