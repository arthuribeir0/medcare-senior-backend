package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.tratamento.Tratamento;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "Tratamento cadastrado")
public record TratamentoResponseDTO(
        @Schema(example = "1000")
        Long id,

        @Schema(example = "100")
        Long medicamentoId,

        @Schema(example = "2026-10-08")
        LocalDate dataInicio,

        @Schema(description = "null = tratamento contínuo", example = "2026-11-07")
        LocalDate dataFim,

        @ArraySchema(schema = @Schema(type = "string", format = "time", example = "08:00:00"))
        List<LocalTime> horarios,

        @ArraySchema(schema = @Schema(implementation = DayOfWeek.class, example = "MONDAY"))
        List<DayOfWeek> diasSemana
) {

    public static TratamentoResponseDTO from(Tratamento tratamento) {
        return new TratamentoResponseDTO(tratamento.getId(), tratamento.getMedicamentoId(), tratamento.getDataInicio(),
                tratamento.getDataFim(), tratamento.getHorarios(), tratamento.getDiasSemana());
    }
}
