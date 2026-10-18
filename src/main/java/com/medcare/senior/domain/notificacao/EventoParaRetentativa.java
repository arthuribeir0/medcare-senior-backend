package com.medcare.senior.domain.notificacao;

import com.medcare.senior.domain.dose.StatusDose;

import java.time.LocalDateTime;

/**
 * Evento de aviso PENDENTE elegível para nova tentativa, com o estado da dose e quem avisar, lidos na mesma consulta.
 *
 * @param eventoId          o evento
 * @param tentativas        tentativas já feitas (a reivindicação espera exatamente este valor)
 * @param doseId            dose do aviso
 * @param statusDose        status da dose no momento da busca (o reenvio só faz sentido com a dose PENDENTE)
 * @param horarioProgramado horário da dose
 * @param semDono           dose anonimizada ou de tratamento, medicamento ou idoso que não existe mais (removido)
 * @param cuidadorId        destinatário (nulo se {@code semDono})
 * @param idosoId           idoso da dose (nulo se {@code semDono})
 * @param nomeIdoso         nome para o texto do aviso (nulo se {@code semDono})
 */
public record EventoParaRetentativa(Long eventoId,
                                    int tentativas,
                                    Long doseId,
                                    StatusDose statusDose,
                                    LocalDateTime horarioProgramado,
                                    boolean semDono,
                                    Long cuidadorId,
                                    Long idosoId,
                                    String nomeIdoso) {
}
