package com.medcare.senior.domain.mensagem;

/**
 * Quem escreveu a mensagem. A conversa é sempre entre um paciente e o seu cuidador: o destinatário é o outro lado.
 */
public enum Remetente {
    CUIDADOR,
    PACIENTE;

    /**
     * O outro lado da conversa (o destinatário de uma mensagem deste remetente).
     */
    public Remetente outroLado() {
        return this == CUIDADOR ? PACIENTE : CUIDADOR;
    }
}
