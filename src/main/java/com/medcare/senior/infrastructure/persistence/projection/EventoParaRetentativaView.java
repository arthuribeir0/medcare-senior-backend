package com.medcare.senior.infrastructure.persistence.projection;

import java.time.LocalDateTime;

/**
 * Linha da busca nativa de eventos para retentativa (projeção por interface: os getters casam com os aliases do SQL).
 */
public interface EventoParaRetentativaView {

    Long getEventoId();

    String getTipo();

    Integer getTentativas();

    Long getDoseId();

    String getStatusDose();

    LocalDateTime getHorarioProgramado();

    Boolean getSemDono();

    Long getCuidadorId();

    Long getPacienteId();

    String getNomePaciente();

    String getNomeMedicamento();

    Integer getToleranciaMinutos();
}
