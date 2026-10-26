package com.medcare.senior.domain.dose;

/**
 * Status mostrado no app. Para dose registrada é o status gravado; para dose PENDENTE depende do relógio:
 * ATRASADA depois do fim da tolerância e NAO_TOMADA depois do encerramento da janela (mesmo antes de o job gravar).
 */
public enum StatusExibicao {
    PENDENTE,
    ATRASADA,
    CONFIRMADA_NO_HORARIO,
    CONFIRMADA_COM_ATRASO,
    NAO_TOMADA;

    /**
     * Exibição de um status já gravado.
     */
    public static StatusExibicao de(StatusDose status) {
        return switch (status) {
            case PENDENTE -> PENDENTE;
            case CONFIRMADA_NO_HORARIO -> CONFIRMADA_NO_HORARIO;
            case NAO_TOMADA -> NAO_TOMADA;
            case CONFIRMADA_COM_ATRASO -> CONFIRMADA_COM_ATRASO;
        };
    }
}
