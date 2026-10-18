package com.medcare.senior.domain.dose;

/**
 * Dose com os dados do idoso e do medicamento, lida em uma única consulta (sem N+1). Só existe para doses de
 * tratamento e medicamento não removidos (dose anonimizada não tem dono).
 */
public record DoseDetalhada(Dose dose,
                            Long idosoId,
                            String idosoNome,
                            Long medicamentoId,
                            String medicamentoNome,
                            String dosagem,
                            String tipo) {
}
