package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import org.springframework.stereotype.Component;

@Component
public class CuidadorMapper {

    public Cuidador toDomain(CuidadorEntity entity) {
        return Cuidador.builder()
                .id(entity.getId())
                .nome(entity.getNome())
                .email(entity.getEmail())
                .senhaHash(entity.getSenhaHash())
                .telefone(entity.getTelefone())
                .dataCadastro(entity.getDataCadastro())
                .build();
    }

    public CuidadorEntity toEntity(Cuidador cuidador) {
        return CuidadorEntity.builder()
                .id(cuidador.getId())
                .nome(cuidador.getNome())
                .email(cuidador.getEmail())
                .senhaHash(cuidador.getSenhaHash())
                .telefone(cuidador.getTelefone())
                .dataCadastro(cuidador.getDataCadastro())
                .build();
    }
}
