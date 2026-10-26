package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.tratamento.Tratamento;

import java.time.LocalDateTime;

/**
 * Tratamento cadastrado ou editado e a primeira dose depois de agora ({@code null} se não houver dose
 * até o mesmo dia da semana seguinte). O app avisa o usuário quando ela não é hoje: os horários de hoje já passaram.
 */
public record TratamentoSalvo(Tratamento tratamento, LocalDateTime primeiraDose) {
}
