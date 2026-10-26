package com.medcare.senior.domain.notificacao;

import java.time.LocalDateTime;

/**
 * Dados para avisar o cuidador de que uma dose não foi confirmada dentro da tolerância. Sem o nome do medicamento de
 * propósito: o push passa pelos servidores do Google (FCM) e o medicamento é dado de saúde (LGPD); o app busca o
 * detalhe da dose pela API, com o token do cuidador.
 *
 * @param doseId            dose não confirmada (payload do push, para o app abrir a dose)
 * @param cuidadorId        destinatário do aviso
 * @param pacienteId           paciente da dose (payload do push)
 * @param nomePaciente         nome mostrado no texto do aviso
 * @param horarioProgramado horário da dose, no fuso da aplicação
 */
public record DoseNaoTomadaNotificacao(Long doseId,
                                       Long cuidadorId,
                                       Long pacienteId,
                                       String nomePaciente,
                                       LocalDateTime horarioProgramado) {
}
