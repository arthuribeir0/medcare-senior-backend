package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;

/**
 * Dose detalhada mais o que depende do relógio, calculado por {@link PoliticaDeJanela#avaliar}.
 */
public record DoseAvaliada(DoseDetalhada detalhe,
                           JanelaDose janela,
                           StatusExibicao statusExibicao,
                           Confirmabilidade confirmabilidade,
                           long toleranciaMinutos) {

    /**
     * Último instante em que a dose ainda pode ser confirmada (horário + tolerância + margem pós-aviso).
     */
    public LocalDateTime limiteConfirmacao() {
        return janela.encerraEm();
    }
}
