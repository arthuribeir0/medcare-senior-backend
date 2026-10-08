package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.TratamentoMapper;
import com.medcare.senior.infrastructure.persistence.repository.TratamentoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TratamentoRepositoryAdapter implements TratamentoRepositoryPort {

    private final TratamentoRepository tratamentoRepository;
    private final TratamentoMapper tratamentoMapper;

    @Override
    public Tratamento save(Tratamento tratamento) {
        return tratamentoMapper.toDomain(tratamentoRepository.save(tratamentoMapper.toEntity(tratamento)));
    }

    @Override
    public List<Tratamento> findAllByMedicamentoId(Long medicamentoId) {
        return tratamentoRepository.findAllByMedicamento_IdAndRemovidoEmIsNullOrderByDataInicioAscIdAsc(medicamentoId)
                .stream()
                .map(tratamentoMapper::toDomain)
                .toList();
    }

    @Override
    public Optional<Tratamento> findByIdAndCuidadorId(Long id, Long cuidadorId) {
        return tratamentoRepository.findAtivoByIdAndCuidadorId(id, cuidadorId).map(tratamentoMapper::toDomain);
    }

    @Override
    public List<Tratamento> findAllAtivos(LocalDate hoje) {
        return tratamentoRepository.findAllAtivos(hoje).stream()
                .map(tratamentoMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void marcarComoRemovidosPorMedicamento(Long medicamentoId, LocalDateTime agora) {
        tratamentoRepository.marcarComoRemovidosPorMedicamento(medicamentoId, agora);
    }

    @Override
    @Transactional
    public void deleteAllByCuidadorId(Long cuidadorId) {
        tratamentoRepository.deleteAllByCuidadorId(cuidadorId);
    }
}
