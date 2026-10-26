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
public class CodigoVinculo {

    private Long id;
    private String codigoUnico;
    private Long cuidadorId;
    private Long pacienteId;
    private LocalDateTime expiraEm;
    private boolean usado;
}
