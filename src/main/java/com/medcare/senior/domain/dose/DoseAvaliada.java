package com.medcare.senior.domain.dose;

/**
 * Dose detalhada mais o que depende do relógio, calculado por {@link PoliticaDeJanela#avaliar}.
 */
public record DoseAvaliada(DoseDetalhada detalhe,
                           JanelaDose janela,
                           StatusExibicao statusExibicao,
                           Confirmabilidade confirmabilidade,
                           long toleranciaMinutos) {
}
