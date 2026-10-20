package com.medcare.senior.infrastructure.persistence.entity;

import com.medcare.senior.domain.mensagem.Remetente;
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
 * Mensagem da conversa de um idoso com o seu cuidador (V14). O idoso fica só pelo ID (as consultas não precisam
 * carregar a entidade). O conteúdo é dado pessoal: esta entidade não tem toString de propósito.
 */
@Entity
@Table(name = "mensagem")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MensagemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idoso_id", nullable = false)
    private Long idosoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "remetente", nullable = false, length = 10)
    private Remetente remetente;

    @Column(name = "conteudo", nullable = false, columnDefinition = "text")
    private String conteudo;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "lida_em")
    private LocalDateTime lidaEm;
}
