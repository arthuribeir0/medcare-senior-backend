package com.medcare.senior.domain.configuracao;

import com.medcare.senior.domain.tratamento.Tratamento;

import java.time.Duration;

/**
 * Modo de teste (app.modo-teste, desligado por padrão): só para testar o app no aparelho. Ligado, libera a tolerância
 * de 1 minuto e qualquer horário no cadastro e na edição do tratamento, sem a antecedência mínima de 5 minutos.
 * Desligado, valem as regras normais (RN-009 e a antecedência do tratamento).
 *
 * @param ativo modo de teste ligado
 */
public record ModoDeTeste(boolean ativo) {

    /** Regras normais. */
    public static final ModoDeTeste DESLIGADO = new ModoDeTeste(false);

    /** Menor tolerância aceita do cuidador, em minutos. */
    public int toleranciaMinima() {
        return ativo ? Tolerancia.MINIMO_MODO_TESTE_MINUTOS : Tolerancia.MINIMO_MINUTOS;
    }

    /** Antecedência mínima de um horário novo de hoje; zero no modo de teste. */
    public Duration antecedenciaMinima() {
        return ativo ? Duration.ZERO : Tratamento.ANTECEDENCIA_MINIMA;
    }
}
