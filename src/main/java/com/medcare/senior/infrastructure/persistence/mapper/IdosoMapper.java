package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import com.medcare.senior.infrastructure.persistence.entity.IdosoEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdosoMapper {

    // Usado para obter uma referência (proxy) ao cuidador a partir do ID, sem consultar o banco
    private final EntityManager entityManager;

    public Idoso toDomain(IdosoEntity entity) {
        return Idoso.builder()
                .id(entity.getId())
                .nome(entity.getNome())
                .dataNascimento(entity.getDataNascimento())
                .telefone(entity.getTelefone())
                .cuidadorId(entity.getCuidador().getId())
                .build();
    }

    public IdosoEntity toEntity(Idoso idoso) {
        return IdosoEntity.builder()
                .id(idoso.getId())
                .nome(idoso.getNome())
                .dataNascimento(idoso.getDataNascimento())
                .telefone(idoso.getTelefone())
                .cuidador(entityManager.getReference(CuidadorEntity.class, idoso.getCuidadorId()))
                .build();
    }
}
