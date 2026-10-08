package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.CodigoVinculoMapper;
import com.medcare.senior.infrastructure.persistence.repository.CodigoVinculoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CodigoVinculoRepositoryAdapter implements CodigoVinculoRepositoryPort {

    private final CodigoVinculoRepository codigoVinculoRepository;
    private final CodigoVinculoMapper codigoVinculoMapper;

    @Override
    public CodigoVinculo save(CodigoVinculo codigoVinculo) {
        return codigoVinculoMapper.toDomain(codigoVinculoRepository.save(codigoVinculoMapper.toEntity(codigoVinculo)));
    }

    @Override
    public boolean existsCodigoAtivo(String codigoUnico, LocalDateTime agora) {
        return codigoVinculoRepository.existsByCodigoUnicoAndUsadoFalseAndExpiraEmAfter(codigoUnico, agora);
    }

    @Override
    public Optional<CodigoVinculo> findMaisRecenteByCodigoUnico(String codigoUnico) {
        return codigoVinculoRepository.findFirstByCodigoUnicoOrderByIdDesc(codigoUnico)
                .map(codigoVinculoMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean marcarComoUsado(Long id, LocalDateTime agora) {
        return codigoVinculoRepository.marcarComoUsado(id, agora) == 1;
    }

    @Override
    @Transactional
    public void deleteAllByCuidadorId(Long cuidadorId) {
        codigoVinculoRepository.deleteAllByCuidadorId(cuidadorId);
    }
}
