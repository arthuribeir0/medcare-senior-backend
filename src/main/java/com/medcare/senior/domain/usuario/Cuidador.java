package com.medcare.senior.domain.usuario;

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
public class Cuidador {

    private Long id;
    private String nome;
    private String email;
    private String senhaHash;
    private String telefone;
    private LocalDateTime dataCadastro;
}
