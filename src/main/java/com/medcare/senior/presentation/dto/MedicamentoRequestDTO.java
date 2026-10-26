package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.medicamento.Medicamento;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados do medicamento (nome, dosagem e tipo obrigatórios — RN-005)")
public record MedicamentoRequestDTO(

        @Schema(example = "Losartana")
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @Schema(example = "50mg")
        @NotBlank(message = "A dosagem é obrigatória")
        @Size(max = 100, message = "A dosagem deve ter no máximo 100 caracteres")
        String dosagem,

        @Schema(description = "Tipo do medicamento (lista fixa, em maiúsculas e sem acento)", example = "COMPRIMIDO",
                allowableValues = {"COMPRIMIDO", "CAPSULA", "GOTAS", "INJECAO", "XAROPE", "ADESIVO"})
        @NotBlank(message = "O tipo é obrigatório")
        @Pattern(regexp = MedicamentoRequestDTO.TIPOS_PERMITIDOS,
                message = "O tipo deve ser um destes: COMPRIMIDO, CAPSULA, GOTAS, INJECAO, XAROPE ou ADESIVO")
        String tipo,

        @Schema(example = "Redondo")
        @Size(max = 100, message = "O formato deve ter no máximo 100 caracteres")
        String formato,

        @Schema(example = "Branco")
        @Size(max = 50, message = "A cor deve ter no máximo 50 caracteres")
        String cor,

        @Schema(example = "30")
        @PositiveOrZero(message = "A quantidade em estoque não pode ser negativa")
        Integer quantidadeEstoque,

        @Schema(example = "https://consultas.anvisa.gov.br/bula/losartana.pdf")
        @Size(max = 2048, message = "A URL da bula deve ter no máximo 2048 caracteres")
        String bulaUrl,

        @Schema(description = "ID do paciente (deve pertencer ao cuidador autenticado)", example = "10")
        @NotNull(message = "O paciente é obrigatório")
        Long pacienteId
) {

    // Tipos aceitos no cadastro e na edição. Sem migration: a coluna continua VARCHAR, e cadastros antigos com texto
    // livre continuam sendo devolvidos como estão.
    public static final String TIPOS_PERMITIDOS = "COMPRIMIDO|CAPSULA|GOTAS|INJECAO|XAROPE|ADESIVO";

    public Medicamento toDomain() {
        return Medicamento.builder()
                .nome(nome)
                .dosagem(dosagem)
                .tipo(tipo)
                .formato(formato)
                .cor(cor)
                .quantidadeEstoque(quantidadeEstoque)
                .bulaUrl(bulaUrl)
                .pacienteId(pacienteId)
                .build();
    }
}
