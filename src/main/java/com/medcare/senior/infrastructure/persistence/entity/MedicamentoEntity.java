package com.medcare.senior.infrastructure.persistence.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "medicamento")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicamentoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nome", nullable = false)
    private String nome;

    @Column(name = "dosagem", nullable = false, length = 100)
    private String dosagem;

    @Column(name = "tipo", nullable = false, length = 100)
    private String tipo;

    @Column(name = "formato", length = 100)
    private String formato;

    @Column(name = "cor", length = 50)
    private String cor;

    @Column(name = "quantidade_estoque")
    private Integer quantidadeEstoque;

    @Column(name = "bula_url", length = 2048)
    private String bulaUrl;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idoso_id", nullable = false)
    private IdosoEntity idoso;
}
