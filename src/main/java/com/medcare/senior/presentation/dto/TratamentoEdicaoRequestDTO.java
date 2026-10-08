package com.medcare.senior.presentation.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Substitui horários, dias da semana e data de término do tratamento. A data de início não pode ser alterada.
 * As regras de negócio (RN-007) são validadas no domínio; aqui ficam só as validações de formato.
 */
@Schema(description = "Novos dados do tratamento (a data de início não muda). RN-007: ao menos um horário, "
        + "sem repetição; ao menos um dia; dataFim igual ou posterior à data de início")
public record TratamentoEdicaoRequestDTO(

        @Schema(description = "Último dia do tratamento; omita (ou envie null) para tratamento contínuo",
                example = "2026-12-31")
        LocalDate dataFim,

        @ArraySchema(arraySchema = @Schema(description = "Horários das doses, sem repetição"),
                schema = @Schema(type = "string", format = "time", example = "08:00"))
        List<@NotNull(message = "Horário não pode ser nulo") LocalTime> horarios,

        @ArraySchema(arraySchema = @Schema(description = "Dias da semana em que o medicamento é tomado"),
                schema = @Schema(implementation = DayOfWeek.class, example = "MONDAY"))
        List<@NotNull(message = "Dia da semana não pode ser nulo") DayOfWeek> diasSemana
) {
}
