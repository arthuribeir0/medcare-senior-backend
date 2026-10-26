package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.infrastructure.persistence.entity.PacienteEntity;
import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MedicamentoMapper {

    // Usado para obter uma referência (proxy) ao paciente a partir do ID, sem consultar o banco
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
                .pacienteId(entity.getPaciente().getId())
                .removidoEm(entity.getRemovidoEm())
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
                .paciente(entityManager.getReference(PacienteEntity.class, medicamento.getPacienteId()))
                .removidoEm(medicamento.getRemovidoEm())
                .build();
    }
}
