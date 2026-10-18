package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;

/**
 * Janela de confirmação de uma dose, calculada a partir do horário programado ({@link PoliticaDeJanela}).
 *
 * @param abreEm    a partir de quando a dose pode ser confirmada
 * @param avisoEm   fim da tolerância: depois disto a dose está atrasada (e o cuidador é avisado)
 * @param encerraEm depois disto a dose não pode mais ser confirmada
 */
public record JanelaDose(LocalDateTime abreEm, LocalDateTime avisoEm, LocalDateTime encerraEm) {
}
