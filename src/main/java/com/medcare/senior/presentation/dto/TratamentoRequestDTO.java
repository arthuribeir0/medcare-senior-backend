package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lista vazia, horário repetido e data de término anterior ao início são regras de negócio (RN-007),
 * validadas no domínio; aqui ficam só as validações de formato.
 */
@Schema(description = "Dados do tratamento (RN-007: ao menos um horário, sem repetição; ao menos um dia; "
        + "dataFim igual ou posterior a dataInicio)")
public record TratamentoRequestDTO(

        @Schema(description = "Primeiro dia do tratamento", example = "2026-10-08")
        @NotNull(message = "A data de início é obrigatória")
        LocalDate dataInicio,

        @Schema(description = "Último dia do tratamento; omita para tratamento contínuo", example = "2026-11-07")
        LocalDate dataFim,

        @ArraySchema(arraySchema = @Schema(description = "Horários das doses, sem repetição"),
                schema = @Schema(type = "string", format = "time", example = "08:00"))
        List<@NotNull(message = "Horário não pode ser nulo") LocalTime> horarios,

        @ArraySchema(arraySchema = @Schema(description = "Dias da semana em que o medicamento é tomado"),
                schema = @Schema(implementation = DayOfWeek.class, example = "MONDAY"))
        List<@NotNull(message = "Dia da semana não pode ser nulo") DayOfWeek> diasSemana
) {
}
