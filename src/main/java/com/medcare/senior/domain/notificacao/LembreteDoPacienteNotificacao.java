package com.medcare.senior.domain.notificacao;

import java.time.LocalDateTime;

/**
 * Dados do lembrete de dose enviado pelo servidor ao aparelho do PRÓPRIO paciente. Diferente do aviso ao cuidador, o
 * texto leva o nome do remédio: o aparelho é do paciente, e é isso que ele precisa saber para tomar a dose. Nenhum outro
 * dado clínico (dosagem, observações) vai junto.
 *
 * @param doseId            dose ainda PENDENTE (payload do push, para o app abrir a dose)
 * @param pacienteId        destinatário
 * @param nomeMedicamento   nome mostrado no texto do lembrete
 * @param horarioProgramado horário da dose, no fuso da aplicação
 * @param validoAte         fim da janela do lembrete (o aviso ao cuidador): depois disso o lembrete não faz sentido
 *                          e o provedor pode descartá-lo se o aparelho estiver sem rede
 */
public record LembreteDoPacienteNotificacao(Long doseId,
                                            Long pacienteId,
                                            String nomeMedicamento,
                                            LocalDateTime horarioProgramado,
                                            LocalDateTime validoAte) {
}
