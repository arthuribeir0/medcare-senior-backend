package com.medcare.senior.domain.exception;

/**
 * O período pedido não tem nenhuma dose registrada: o relatório não é gerado e o usuário é avisado (UC-012, fluxo 4a).
 */
public class PeriodoSemRegistrosException extends RuntimeException {

    public PeriodoSemRegistrosException() {
        super("Não há doses registradas neste período. Escolha outro período para exportar.");
    }
}
