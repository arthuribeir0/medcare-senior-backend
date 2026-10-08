package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import com.medcare.senior.infrastructure.persistence.entity.TratamentoEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DoseMapper {

    // Usado para obter uma referência (proxy) ao tratamento a partir do ID, sem consultar o banco
    private final EntityManager entityManager;

    public Dose toDomain(DoseEntity entity) {
        return Dose.builder()
                .id(entity.getId())
                .tratamentoId(entity.getTratamento() == null ? null : entity.getTratamento().getId())
                .horarioProgramado(entity.getHorarioProgramado())
                .horaConfirmacao(entity.getHoraConfirmacao())
                .status(entity.getStatus())
                .build();
    }

    public DoseEntity toEntity(Dose dose) {
        return DoseEntity.builder()
                .id(dose.getId())
                .tratamento(dose.getTratamentoId() == null
                        ? null
                        : entityManager.getReference(TratamentoEntity.class, dose.getTratamentoId()))
                .horarioProgramado(dose.getHorarioProgramado())
                .horaConfirmacao(dose.getHoraConfirmacao())
                .status(dose.getStatus())
                .build();
    }
}
