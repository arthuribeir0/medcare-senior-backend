package com.medcare.senior.domain.notificacao;

/**
 * Para quem é o evento de notificação de uma dose (evento_notificacao.tipo). Cada dose tem no máximo um evento de cada
 * tipo. Gravado pelo nome: não renomeie.
 */
public enum TipoEventoNotificacao {

    /** Aviso ao cuidador de que a dose não foi confirmada dentro da tolerância (RN-009, RN-010). */
    AVISO_CUIDADOR,

    /**
     * Reforço do lembrete da dose, enviado pelo servidor ao aparelho do paciente (UC-006). O lembrete principal é o que
     * o próprio aparelho agenda; este cobre o caso de o aparelho não ter tocado (reiniciado, app parado pelo fabricante).
     */
    LEMBRETE_PACIENTE
}
