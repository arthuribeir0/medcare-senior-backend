package com.medcare.senior.infrastructure.persistence.entity;

import com.medcare.senior.domain.dose.StatusDose;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dose")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nulo = dose anonimizada (tratamento apagado na exclusão de conta; FK ON DELETE SET NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tratamento_id")
    private TratamentoEntity tratamento;

    @Column(name = "horario_programado", nullable = false)
    private LocalDateTime horarioProgramado;

    @Column(name = "hora_confirmacao")
    private LocalDateTime horaConfirmacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusDose status;
}
