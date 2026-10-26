package com.medcare.senior.infrastructure.persistence.mapper;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import org.springframework.stereotype.Component;

/**
 * Leitura das doses do banco para o domínio. A gravação de doses novas é em lote, pelo
 * DoseRepositoryAdapter.inserirPendentes, sem passar por entidades.
 */
@Component
public class DoseMapper {

    public Dose toDomain(DoseEntity entity) {
        return Dose.builder()
                .id(entity.getId())
                .tratamentoId(entity.getTratamento() == null ? null : entity.getTratamento().getId())
                .horarioProgramado(entity.getHorarioProgramado())
                .horaConfirmacao(entity.getHoraConfirmacao())
                .status(entity.getStatus())
                .confirmadoPor(entity.getConfirmadoPor())
                .build();
    }
}
