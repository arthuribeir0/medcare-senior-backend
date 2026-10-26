package com.medcare.senior.domain.configuracao;

/**
 * Limites da tolerância configurável pelo cuidador (RN-009), em minutos, inclusivos. A dose encerra sempre depois do
 * aviso (horário + tolerância + margem pós-aviso), qualquer que seja a tolerância escolhida.
 */
public final class Tolerancia {

    public static final int MINIMO_MINUTOS = 5;
    public static final int MAXIMO_MINUTOS = 120;

    private Tolerancia() {
    }

    /**
     * @throws ToleranciaInvalidaException se {@code minutos} estiver fora de [5, 120]
     */
    public static int validar(int minutos) {
        if (minutos < MINIMO_MINUTOS || minutos > MAXIMO_MINUTOS) {
            throw new ToleranciaInvalidaException();
        }
        return minutos;
    }
}
