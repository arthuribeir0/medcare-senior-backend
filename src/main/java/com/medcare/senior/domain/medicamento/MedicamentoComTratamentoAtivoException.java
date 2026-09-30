package com.medcare.senior.domain.medicamento;

/**
 * RN-006: um medicamento com tratamento ativo não pode ser removido sem confirmação explícita
 * (forcarRemocao=true).
 *
 * <p>Ainda não é lançada em nenhum lugar: a entidade Tratamento só existirá na Fase 2 do plano.
 * Já está mapeada para HTTP 409 no GlobalExceptionHandler.
 */
public class MedicamentoComTratamentoAtivoException extends RuntimeException {

    public MedicamentoComTratamentoAtivoException() {
        super("O medicamento possui tratamento ativo. Para removê-lo mesmo assim, use forcarRemocao=true");
    }
}
