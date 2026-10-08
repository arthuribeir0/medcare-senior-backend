package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.mapper.DoseMapper;
import com.medcare.senior.infrastructure.persistence.repository.DoseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class DoseRepositoryAdapter implements DoseRepositoryPort {

    private final DoseRepository doseRepository;
    private final DoseMapper doseMapper;

    @Override
    @Transactional
    public List<Dose> saveAll(List<Dose> doses) {
        return doseRepository.saveAll(doses.stream().map(doseMapper::toEntity).toList()).stream()
                .map(doseMapper::toDomain)
                .toList();
    }

    @Override
    public Set<LocalDateTime> findHorariosProgramadosByTratamentoIdDesde(Long tratamentoId, LocalDateTime desde) {
        return new HashSet<>(doseRepository.findHorariosProgramadosDesde(tratamentoId, desde));
    }

    @Override
    @Transactional
    public void deletePendentesFuturasByTratamentoId(Long tratamentoId, LocalDateTime agora) {
        doseRepository.deleteFuturasByTratamentoIdAndStatus(tratamentoId, StatusDose.PENDENTE, agora);
    }

    @Override
    @Transactional
    public void deletePendentesFuturasByMedicamentoId(Long medicamentoId, LocalDateTime agora) {
        doseRepository.deleteFuturasByMedicamentoIdAndStatus(medicamentoId, StatusDose.PENDENTE, agora);
    }

    @Override
    @Transactional
    public void deletePendentesByCuidadorId(Long cuidadorId) {
        doseRepository.deleteByCuidadorIdAndStatus(cuidadorId, StatusDose.PENDENTE);
    }
}
