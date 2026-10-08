package com.medcare.senior.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "tratamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TratamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medicamento_id", nullable = false)
    private MedicamentoEntity medicamento;

    @Column(name = "data_inicio", nullable = false)
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

    // Guardados como texto ("08:00", "MONDAY") para o JSON não depender de módulos de data do Jackson;
    // a conversão de/para LocalTime e DayOfWeek fica no TratamentoMapper.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "horarios", nullable = false)
    private List<String> horarios;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dias_semana", nullable = false)
    private List<String> diasSemana;

    @Column(name = "removido_em")
    private LocalDateTime removidoEm;
}
