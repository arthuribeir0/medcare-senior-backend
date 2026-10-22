package com.medcare.senior.infrastructure.config;

import com.medcare.senior.application.usecase.ReenviarAvisosPendentesUseCase;
import com.medcare.senior.application.usecase.VerificarDosesVencidasUseCase;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Parâmetros da varredura de doses, da retentativa de avisos e da janela de confirmação (app.alertas.*). Valores
 * inválidos (negativos, zero, lote zero) impedem a aplicação de subir.
 */
@Configuration
public class AlertasConfig {

    @Bean
    public VerificarDosesVencidasUseCase.Parametros parametrosDeAlerta(
            @Value("${app.alertas.lote-maximo:500}") int loteMaximo) {
        return new VerificarDosesVencidasUseCase.Parametros(loteMaximo);
    }

    /**
     * Retentativa de avisos (RN-010): tentativas no total (contando a 1ª, da varredura), espera base que dobra a cada
     * tentativa e lote por execução.
     */
    @Bean
    public ReenviarAvisosPendentesUseCase.Parametros parametrosDeRetentativa(
            @Value("${app.alertas.retentativa.max-tentativas:5}") int maxTentativas,
            @Value("${app.alertas.retentativa.intervalo-base-minutos:5}") long intervaloBaseMinutos,
            @Value("${app.alertas.retentativa.lote-maximo:200}") int loteMaximo) {
        return new ReenviarAvisosPendentesUseCase.Parametros(maxTentativas, Duration.ofMinutes(intervaloBaseMinutos),
                loteMaximo);
    }

    /**
     * Janela da dose. app.alertas.tolerancia-minutos é a tolerância PADRÃO (quando o cuidador não tem a sua); a
     * dose encerra em horário + tolerância + app.alertas.margem-pos-aviso-minutos. O construtor de PoliticaDeJanela
     * valida que todos são positivos.
     */
    @Bean
    public PoliticaDeJanela politicaDeJanela(
            @Value("${app.alertas.antecedencia-confirmacao-minutos:60}") long antecedenciaMinutos,
            @Value("${app.alertas.tolerancia-minutos:30}") long toleranciaMinutos,
            @Value("${app.alertas.margem-pos-aviso-minutos:30}") long margemPosAvisoMinutos) {
        return new PoliticaDeJanela(Duration.ofMinutes(antecedenciaMinutos), Duration.ofMinutes(toleranciaMinutos),
                Duration.ofMinutes(margemPosAvisoMinutos));
    }
}
