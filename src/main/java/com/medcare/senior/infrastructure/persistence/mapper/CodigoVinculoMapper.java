package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.infrastructure.persistence.entity.CodigoVinculoEntity;
import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import com.medcare.senior.infrastructure.persistence.entity.PacienteEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodigoVinculoMapper {

    // Usado para obter referências (proxies) a cuidador e paciente a partir dos IDs, sem consultar o banco
    private final EntityManager entityManager;

    public CodigoVinculo toDomain(CodigoVinculoEntity entity) {
        return CodigoVinculo.builder()
                .id(entity.getId())
                .codigoUnico(entity.getCodigoUnico())
                .cuidadorId(entity.getCuidador().getId())
                .pacienteId(entity.getPaciente().getId())
                .expiraEm(entity.getExpiraEm())
                .usado(entity.isUsado())
                .build();
    }

    public CodigoVinculoEntity toEntity(CodigoVinculo codigoVinculo) {
        return CodigoVinculoEntity.builder()
                .id(codigoVinculo.getId())
                .codigoUnico(codigoVinculo.getCodigoUnico())
                .cuidador(entityManager.getReference(CuidadorEntity.class, codigoVinculo.getCuidadorId()))
                .paciente(entityManager.getReference(PacienteEntity.class, codigoVinculo.getPacienteId()))
                .expiraEm(codigoVinculo.getExpiraEm())
                .usado(codigoVinculo.isUsado())
                .build();
    }
}
