package com.medcare.senior.infrastructure.persistence.entity;

import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.StatusEventoNotificacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Evento de aviso de uma dose não confirmada (V12). A dose fica só pelo ID: o evento é gravado e atualizado por
 * comandos atômicos no repositório, nunca carregando a dose.
 */
@Entity
@Table(name = "evento_notificacao")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoNotificacaoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusEventoNotificacao status;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "ultima_tentativa")
    private LocalDateTime ultimaTentativa;

    @Column(name = "dose_id", nullable = false, unique = true)
    private Long doseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "motivo_falha", length = 50)
    private MotivoFalhaNotificacao motivoFalha;
}
