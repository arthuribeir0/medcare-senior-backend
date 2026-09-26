package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.MedicamentoMapper;
import com.medcare.senior.infrastructure.persistence.repository.MedicamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MedicamentoRepositoryAdapter implements MedicamentoRepositoryPort {

    private final MedicamentoRepository medicamentoRepository;
    private final MedicamentoMapper medicamentoMapper;

    @Override
    public Medicamento save(Medicamento medicamento) {
        return medicamentoMapper.toDomain(medicamentoRepository.save(medicamentoMapper.toEntity(medicamento)));
    }

    @Override
    public Optional<Medicamento> findById(Long id) {
        return medicamentoRepository.findById(id).map(medicamentoMapper::toDomain);
    }

    @Override
    public List<Medicamento> findAllByIdosoId(Long idosoId) {
        return medicamentoRepository.findAllByIdoso_IdOrderByNomeAsc(idosoId).stream()
                .map(medicamentoMapper::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        medicamentoRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return medicamentoRepository.existsById(id);
    }
}
