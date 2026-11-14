package com.medcare.senior.domain.notificacao;

import com.medcare.senior.domain.dose.StatusDose;

import java.time.LocalDateTime;

/**
 * Evento de notificação PENDENTE elegível para nova tentativa, com o estado da dose e quem avisar, lidos na mesma
 * consulta.
 *
 * @param eventoId          o evento
 * @param tipo              aviso ao cuidador ou lembrete ao paciente
 * @param tentativas        tentativas já feitas (a reivindicação espera exatamente este valor)
 * @param doseId            dose do aviso
 * @param statusDose        status da dose no momento da busca (o reenvio só faz sentido com a dose PENDENTE)
 * @param horarioProgramado horário da dose
 * @param semDono           dose anonimizada ou de tratamento, medicamento ou paciente que não existe mais (removido)
 * @param cuidadorId        destinatário (nulo se {@code semDono})
 * @param pacienteId           paciente da dose (nulo se {@code semDono})
 * @param nomePaciente         nome para o texto do aviso (nulo se {@code semDono})
 * @param nomeMedicamento   nome para o texto do lembrete ao paciente (nulo se {@code semDono})
 * @param toleranciaMinutos tolerância própria do cuidador (nula = a padrão), para saber até quando o lembrete vale
 */
public record EventoParaRetentativa(Long eventoId,
                                    TipoEventoNotificacao tipo,
                                    int tentativas,
                                    Long doseId,
                                    StatusDose statusDose,
                                    LocalDateTime horarioProgramado,
                                    boolean semDono,
                                    Long cuidadorId,
                                    Long pacienteId,
                                    String nomePaciente,
                                    String nomeMedicamento,
                                    Integer toleranciaMinutos) {
}
