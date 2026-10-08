package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import com.medcare.senior.infrastructure.persistence.entity.TratamentoEntity;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class TratamentoMapper {

    // Usado para obter uma referência (proxy) ao medicamento a partir do ID, sem consultar o banco
    private final EntityManager entityManager;

    public Tratamento toDomain(TratamentoEntity entity) {
        return Tratamento.builder()
                .id(entity.getId())
                .medicamentoId(entity.getMedicamento().getId())
                .dataInicio(entity.getDataInicio())
                .dataFim(entity.getDataFim())
                .horarios(entity.getHorarios().stream().map(LocalTime::parse).toList())
                .diasSemana(entity.getDiasSemana().stream().map(DayOfWeek::valueOf).toList())
                .removidoEm(entity.getRemovidoEm())
                .build();
    }

    public TratamentoEntity toEntity(Tratamento tratamento) {
        return TratamentoEntity.builder()
                .id(tratamento.getId())
                .medicamento(entityManager.getReference(MedicamentoEntity.class, tratamento.getMedicamentoId()))
                .dataInicio(tratamento.getDataInicio())
                .dataFim(tratamento.getDataFim())
                .horarios(tratamento.getHorarios().stream().map(LocalTime::toString).toList())
                .diasSemana(tratamento.getDiasSemana().stream().map(DayOfWeek::name).toList())
                .removidoEm(tratamento.getRemovidoEm())
                .build();
    }
}
