package com.medcare.senior.domain.relatorio;

import com.medcare.senior.domain.dose.DoseDetalhada;

import java.util.List;
import java.util.function.Consumer;

/**
 * Gera o arquivo PDF do relatório de adesão (UC-012).
 */
public interface GeradorDePdfPort {

    /**
     * @param doses entrega as doses do período em lotes, em ordem de horário programado
     * @return o arquivo PDF
     */
    byte[] gerar(RelatorioDeAdesao relatorio, FonteDeDoses doses);

    /**
     * Percorre as doses em lotes (cada lote é processado e descartado antes do próximo).
     */
    @FunctionalInterface
    interface FonteDeDoses {
        void percorrer(Consumer<List<DoseDetalhada>> lote);
    }
}
