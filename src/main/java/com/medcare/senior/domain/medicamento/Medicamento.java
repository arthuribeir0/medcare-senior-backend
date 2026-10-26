package com.medcare.senior.domain.medicamento;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Medicamento {

    private Long id;
    private String nome;
    private String dosagem;
    private String tipo;
    private String formato;
    private String cor;
    private Integer quantidadeEstoque;
    private String bulaUrl;
    private Long pacienteId;

    /**
     * Remoção lógica: preenchido quando o cuidador remove o medicamento. Medicamento removido é invisível na API.
     */
    private LocalDateTime removidoEm;

    /**
     * RN-005: nome, dosagem e tipo são obrigatórios.
     *
     * @throws DadosObrigatoriosMedicamentoException se algum deles estiver ausente ou em branco
     */
    public void validarCamposObrigatorios() {
        if (isBlank(nome) || isBlank(dosagem) || isBlank(tipo)) {
            throw new DadosObrigatoriosMedicamentoException();
        }
    }

    private static boolean isBlank(String valor) {
        return valor == null || valor.isBlank();
    }
}
