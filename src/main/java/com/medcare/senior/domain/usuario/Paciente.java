/*
 * Entidade de domínio do paciente (antes chamado idoso): nome, data de nascimento, telefone e o cuidador
 * responsável (RN-004). Sem anotações de framework.
 */
package com.medcare.senior.domain.usuario;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Paciente {

    private Long id;
    private String nome;
    private LocalDate dataNascimento;
    private String telefone;
    private Long cuidadorId;
}
