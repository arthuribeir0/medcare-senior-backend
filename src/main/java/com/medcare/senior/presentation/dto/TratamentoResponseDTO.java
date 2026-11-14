/*
 * DTO de saída (corpo da resposta): contrato JSON de tratamento. Campos: id, medicamentoId, dataInicio, dataFim,
 * horarios, diasSemana, primeiraDose. A API devolve DTOs, nunca as entidades.
 */
package com.medcare.senior.presentation.dto;

import com.medcare.senior.application.usecase.TratamentoSalvo;
import com.medcare.senior.domain.tratamento.Tratamento;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
        List<DayOfWeek> diasSemana,

        @Schema(description = "Só no cadastro e na edição: data e hora da primeira dose a partir de agora (fuso da "
                + "aplicação). Se a data não é hoje, os horários de hoje já passaram e o app avisa o usuário. null na "
                + "listagem ou se não houver dose até o mesmo dia da semana seguinte.",
                example = "2026-10-09T08:00:00", nullable = true)
        LocalDateTime primeiraDose
) {

    /** Listagem: sem primeiraDose. */
    public static TratamentoResponseDTO from(Tratamento tratamento) {
        return of(tratamento, null);
    }

    /** Cadastro e edição: com a primeira dose a partir de agora. */
    public static TratamentoResponseDTO from(TratamentoSalvo salvo) {
        return of(salvo.tratamento(), salvo.primeiraDose());
    }

    private static TratamentoResponseDTO of(Tratamento tratamento, LocalDateTime primeiraDose) {
        return new TratamentoResponseDTO(tratamento.getId(), tratamento.getMedicamentoId(), tratamento.getDataInicio(),
                tratamento.getDataFim(), tratamento.getHorarios(), tratamento.getDiasSemana(), primeiraDose);
    }
}
