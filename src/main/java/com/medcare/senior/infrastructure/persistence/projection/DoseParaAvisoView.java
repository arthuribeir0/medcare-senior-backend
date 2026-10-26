package com.medcare.senior.infrastructure.persistence.projection;

import java.time.LocalDateTime;

/**
 * Linha da busca nativa de doses para aviso (projeção por interface: os nomes dos getters casam com os aliases do SQL).
 */
public interface DoseParaAvisoView {

    Long getDoseId();

    LocalDateTime getHorarioProgramado();

    Long getCuidadorId();

    Long getPacienteId();

    String getNomePaciente();
}
