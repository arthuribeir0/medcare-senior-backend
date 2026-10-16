package com.medcare.senior.infrastructure.persistence.projection;

import java.time.LocalDateTime;

/**
 * Linha da busca de doses vencidas (construtor JPQL): a dose e os dados do aviso em uma única consulta.
 */
public record DoseVencidaProjection(Long doseId,
                                    Long tratamentoId,
                                    LocalDateTime horarioProgramado,
                                    Long cuidadorId,
                                    String nomeIdoso,
                                    String nomeMedicamento) {
}
