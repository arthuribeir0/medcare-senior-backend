package com.medcare.senior.domain.configuracao;

/**
 * Limites da tolerância configurável pelo cuidador (RN-009), em minutos, inclusivos. A dose encerra sempre depois do
 * aviso (horário + tolerância + margem pós-aviso), qualquer que seja a tolerância escolhida.
 */
public final class Tolerancia {

    public static final int MINIMO_MINUTOS = 5;
    public static final int MAXIMO_MINUTOS = 120;
    /** Menor tolerância no modo de teste ({@link ModoDeTeste}); é também o mínimo do CHECK do banco. */
    public static final int MINIMO_MODO_TESTE_MINUTOS = 1;

    private Tolerancia() {
    }

    /**
     * @throws ToleranciaInvalidaException se {@code minutos} estiver fora de [5, 120]
     */
    public static int validar(int minutos) {
        return validar(minutos, MINIMO_MINUTOS);
    }

    /**
     * @param minimo menor tolerância aceita ({@link ModoDeTeste#toleranciaMinima()})
     * @throws ToleranciaInvalidaException se {@code minutos} estiver fora de [minimo, 120]
     */
    public static int validar(int minutos, int minimo) {
        if (minutos < minimo || minutos > MAXIMO_MINUTOS) {
            throw new ToleranciaInvalidaException(minimo);
        }
        return minutos;
    }
}
