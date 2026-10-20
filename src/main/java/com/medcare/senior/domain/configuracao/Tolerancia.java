package com.medcare.senior.domain.configuracao;

/**
 * Limites da tolerância configurável pelo cuidador (RN-009), em minutos, inclusivos. O máximo fica abaixo do
 * encerramento da dose (180 min, fixo), invariante conferida pela PoliticaDeJanela.
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
