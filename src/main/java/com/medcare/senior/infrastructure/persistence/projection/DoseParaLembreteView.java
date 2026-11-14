package com.medcare.senior.infrastructure.persistence.projection;

import java.time.LocalDateTime;

/**
 * Linha da busca nativa de doses para o lembrete ao paciente (projeção por interface: os getters casam com os aliases
 * do SQL).
 */
public interface DoseParaLembreteView {

    Long getDoseId();

    LocalDateTime getHorarioProgramado();

    Long getPacienteId();

    String getNomeMedicamento();

    Integer getToleranciaMinutos();
}
