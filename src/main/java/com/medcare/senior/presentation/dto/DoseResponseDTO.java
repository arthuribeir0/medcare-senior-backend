package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.JanelaDose;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.dose.StatusExibicao;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Dose programada, com a janela de confirmação e os status calculados no servidor")
public record DoseResponseDTO(
        @Schema(example = "5000")
        Long id,

        @Schema(example = "10")
        Long idosoId,

        @Schema(example = "José Souza")
        String idosoNome,

        @Schema(example = "100")
        Long medicamentoId,

        @Schema(example = "Losartana")
        String medicamentoNome,

        @Schema(example = "50mg")
        String dosagem,

        @Schema(example = "COMPRIMIDO")
        String tipo,

        @Schema(example = "1000")
        Long tratamentoId,

        @Schema(example = "2026-10-08T08:00:00")
        LocalDateTime horarioProgramado,

        @Schema(description = "Quando a dose foi confirmada (null se não foi)", example = "2026-10-08T08:04:00", nullable = true)
        LocalDateTime horaConfirmacao,

        @Schema(description = "Status gravado no banco", example = "PENDENTE")
        StatusDose status,

        @Schema(description = "Status a exibir: para dose PENDENTE, ATRASADA depois da tolerância e NAO_TOMADA depois "
                + "do encerramento da janela", example = "ATRASADA")
        StatusExibicao statusExibicao,

        @Schema(description = "Minutos de tolerância depois do horário programado", example = "30")
        long toleranciaMinutos,

        JanelaDTO janela,

        @Schema(description = "Se a dose pode ser confirmada agora", example = "OK")
        Confirmabilidade confirmabilidade
) {

    @Schema(description = "Janela de confirmação (limites inclusivos)")
    public record JanelaDTO(
            @Schema(description = "A partir de quando pode ser confirmada", example = "2026-10-08T07:00:00")
            LocalDateTime abreEm,

            @Schema(description = "Fim da tolerância: depois disto a confirmação é com atraso", example = "2026-10-08T08:30:00")
            LocalDateTime avisoEm,

            @Schema(description = "Depois disto não pode mais ser confirmada", example = "2026-10-08T11:00:00")
            LocalDateTime encerraEm
    ) {
    }

    public static DoseResponseDTO from(DoseAvaliada avaliada) {
        DoseDetalhada detalhe = avaliada.detalhe();
        JanelaDose janela = avaliada.janela();
        return new DoseResponseDTO(detalhe.dose().getId(), detalhe.idosoId(), detalhe.idosoNome(), detalhe.medicamentoId(),
                detalhe.medicamentoNome(), detalhe.dosagem(), detalhe.tipo(), detalhe.dose().getTratamentoId(),
                detalhe.dose().getHorarioProgramado(), detalhe.dose().getHoraConfirmacao(), detalhe.dose().getStatus(),
                avaliada.statusExibicao(), avaliada.toleranciaMinutos(),
                new JanelaDTO(janela.abreEm(), janela.avisoEm(), janela.encerraEm()), avaliada.confirmabilidade());
    }
}
