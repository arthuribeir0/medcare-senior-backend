package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.infrastructure.persistence.entity.CuidadorEntity;
import com.medcare.senior.infrastructure.persistence.entity.PacienteEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PacienteMapper {

    // Usado para obter uma referência (proxy) ao cuidador a partir do ID, sem consultar o banco
    private final EntityManager entityManager;

    public Paciente toDomain(PacienteEntity entity) {
        return Paciente.builder()
                .id(entity.getId())
                .nome(entity.getNome())
                .dataNascimento(entity.getDataNascimento())
                .telefone(entity.getTelefone())
                .cuidadorId(entity.getCuidador().getId())
                .build();
    }

    public PacienteEntity toEntity(Paciente paciente) {
        return PacienteEntity.builder()
                .id(paciente.getId())
                .nome(paciente.getNome())
                .dataNascimento(paciente.getDataNascimento())
                .telefone(paciente.getTelefone())
                .cuidador(entityManager.getReference(CuidadorEntity.class, paciente.getCuidadorId()))
                .build();
    }
}
