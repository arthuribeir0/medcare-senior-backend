package com.medcare.senior.presentation.dto;

import com.medcare.senior.domain.medicamento.Medicamento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MedicamentoRequestDTO(

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nome,

        @NotBlank(message = "A dosagem é obrigatória")
        @Size(max = 100, message = "A dosagem deve ter no máximo 100 caracteres")
        String dosagem,

        @NotBlank(message = "O tipo é obrigatório")
        @Size(max = 100, message = "O tipo deve ter no máximo 100 caracteres")
        String tipo,

        @Size(max = 100, message = "O formato deve ter no máximo 100 caracteres")
        String formato,

        @Size(max = 50, message = "A cor deve ter no máximo 50 caracteres")
        String cor,

        @PositiveOrZero(message = "A quantidade em estoque não pode ser negativa")
        Integer quantidadeEstoque,

        @Size(max = 2048, message = "A URL da bula deve ter no máximo 2048 caracteres")
        String bulaUrl,

        @NotNull(message = "O idoso é obrigatório")
        Long idosoId
) {

    public Medicamento toDomain() {
        return Medicamento.builder()
                .nome(nome)
                .dosagem(dosagem)
                .tipo(tipo)
                .formato(formato)
                .cor(cor)
                .quantidadeEstoque(quantidadeEstoque)
                .bulaUrl(bulaUrl)
                .idosoId(idosoId)
                .build();
    }
}
