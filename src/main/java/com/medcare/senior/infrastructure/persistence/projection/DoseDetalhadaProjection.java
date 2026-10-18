package com.medcare.senior.infrastructure.persistence.projection;

import com.medcare.senior.domain.dose.StatusDose;

import java.time.LocalDateTime;

/**
 * Linha das consultas de doses do app (construtor JPQL): a dose, o idoso e o medicamento em uma única consulta.
 */
public record DoseDetalhadaProjection(Long doseId,
                                      Long tratamentoId,
                                      LocalDateTime horarioProgramado,
                                      LocalDateTime horaConfirmacao,
                                      StatusDose status,
                                      Long idosoId,
                                      String idosoNome,
                                      Long medicamentoId,
                                      String medicamentoNome,
                                      String dosagem,
                                      String tipo) {
}
