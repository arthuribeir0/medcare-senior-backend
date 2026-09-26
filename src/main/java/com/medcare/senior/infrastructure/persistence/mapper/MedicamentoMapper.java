package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.infrastructure.persistence.entity.IdosoEntity;
import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MedicamentoMapper {

    // Usado para obter uma referência (proxy) ao idoso a partir do ID, sem consultar o banco
    private final EntityManager entityManager;

    public Medicamento toDomain(MedicamentoEntity entity) {
        return Medicamento.builder()
                .id(entity.getId())
                .nome(entity.getNome())
                .dosagem(entity.getDosagem())
                .tipo(entity.getTipo())
                .formato(entity.getFormato())
                .cor(entity.getCor())
                .quantidadeEstoque(entity.getQuantidadeEstoque())
                .bulaUrl(entity.getBulaUrl())
                .idosoId(entity.getIdoso().getId())
                .build();
    }

    public MedicamentoEntity toEntity(Medicamento medicamento) {
        return MedicamentoEntity.builder()
                .id(medicamento.getId())
                .nome(medicamento.getNome())
                .dosagem(medicamento.getDosagem())
                .tipo(medicamento.getTipo())
                .formato(medicamento.getFormato())
                .cor(medicamento.getCor())
                .quantidadeEstoque(medicamento.getQuantidadeEstoque())
                .bulaUrl(medicamento.getBulaUrl())
                .idoso(entityManager.getReference(IdosoEntity.class, medicamento.getIdosoId()))
                .build();
    }
}
