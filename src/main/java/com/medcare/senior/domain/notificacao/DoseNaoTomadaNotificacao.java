package com.medcare.senior.domain.notificacao;

import java.time.LocalDateTime;

/**
 * Dados para avisar o cuidador de que uma dose não foi confirmada dentro da tolerância.
 */
public record DoseNaoTomadaNotificacao(Long doseId,
                                       Long cuidadorId,
                                       String nomeIdoso,
                                       String nomeMedicamento,
                                       LocalDateTime horarioProgramado) {
}
