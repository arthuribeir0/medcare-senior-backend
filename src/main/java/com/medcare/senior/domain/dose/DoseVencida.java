package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;

/**
 * Dose PENDENTE cujo horário + tolerância já passou, com os dados de quem deve ser avisado.
 * Só existe para doses de tratamento e medicamento não removidos (doses anonimizadas não têm dono).
 *
 * @param dose            a dose ainda PENDENTE
 * @param cuidadorId      cuidador responsável pelo idoso (destinatário do aviso)
 * @param nomeIdoso       nome do idoso que deveria ter tomado a dose
 * @param nomeMedicamento nome do medicamento da dose
 */
public record DoseVencida(Dose dose, Long cuidadorId, String nomeIdoso, String nomeMedicamento) {

    public Long doseId() {
        return dose.getId();
    }

    public LocalDateTime horarioProgramado() {
        return dose.getHorarioProgramado();
    }
}
