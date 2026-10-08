package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.MedicamentoMapper;
import com.medcare.senior.infrastructure.persistence.repository.MedicamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
        return medicamentoRepository.findByIdAndRemovidoEmIsNull(id).map(medicamentoMapper::toDomain);
    }

    @Override
    public List<Medicamento> findAllByIdosoId(Long idosoId) {
        return medicamentoRepository.findAllByIdoso_IdAndRemovidoEmIsNullOrderByNomeAsc(idosoId).stream()
                .map(medicamentoMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(Long id) {
        return medicamentoRepository.existsByIdAndRemovidoEmIsNull(id);
    }

    @Override
    @Transactional
    public void marcarComoRemovido(Long id, LocalDateTime agora) {
        medicamentoRepository.marcarComoRemovido(id, agora);
    }

    @Override
    @Transactional
    public void deleteAllByCuidadorId(Long cuidadorId) {
        medicamentoRepository.deleteAllByCuidadorId(cuidadorId);
    }
}
