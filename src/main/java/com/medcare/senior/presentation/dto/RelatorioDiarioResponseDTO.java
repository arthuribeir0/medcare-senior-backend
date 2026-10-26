package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.relatorio.RelatorioDiario;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

@Schema(description = "Relatório diário de uso dos medicamentos de um paciente (UC-008)")
public record RelatorioDiarioResponseDTO(
        PacienteResumoDTO paciente,
        @Schema(example = "2026-10-18")
        LocalDate data,
        TotaisDTO totais,
        @Schema(description = "Doses do dia, por horário (mesmo formato da listagem de doses)")
        List<DoseResponseDTO> doses
) {

    public record PacienteResumoDTO(@Schema(example = "10") Long id, @Schema(example = "José Souza") String nome) {
    }

    public record TotaisDTO(
            int total,
            int confirmadasNoHorario,
            int confirmadasComAtraso,
            int naoTomadas,
            int pendentes,
            @Schema(description = "(confirmadas no horário + confirmadas com atraso) / (total - pendentes) * 100, 1 casa; nulo sem "
                    + "dose encerrada no dia", example = "75.0", nullable = true)
            Double aderenciaPercentual
    ) {
        static TotaisDTO from(TotaisDoDia totais) {
            return new TotaisDTO(totais.total(), totais.confirmadasNoHorario(), totais.confirmadasComAtraso(), totais.naoTomadas(),
                    totais.pendentes(), totais.aderenciaPercentual());
        }
    }

    public static RelatorioDiarioResponseDTO from(RelatorioDiario relatorio) {
        return new RelatorioDiarioResponseDTO(new PacienteResumoDTO(relatorio.pacienteId(), relatorio.pacienteNome()),
                relatorio.data(), TotaisDTO.from(relatorio.totais()),
                relatorio.doses().stream().map(DoseResponseDTO::from).toList());
    }
}
