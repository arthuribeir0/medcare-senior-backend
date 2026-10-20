package com.medcare.senior.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuração de alerta do cuidador (V13). A chave é o próprio cuidador_id (uma linha por cuidador, no máximo); o
 * cuidador fica só pelo ID, e a gravação é um upsert nativo no repositório.
 */
@Entity
@Table(name = "configuracao_alerta")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfiguracaoAlertaEntity {

    @Id
    @Column(name = "cuidador_id")
    private Long cuidadorId;

    @Column(name = "tolerancia_minutos", nullable = false)
    private Integer toleranciaMinutos;
}
