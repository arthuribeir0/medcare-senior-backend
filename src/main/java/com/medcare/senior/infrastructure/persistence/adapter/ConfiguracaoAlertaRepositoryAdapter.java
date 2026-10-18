package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.configuracao.ConfiguracaoAlertaRepositoryPort;
import com.medcare.senior.infrastructure.persistence.entity.ConfiguracaoAlertaEntity;
import com.medcare.senior.infrastructure.persistence.repository.ConfiguracaoAlertaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConfiguracaoAlertaRepositoryAdapter implements ConfiguracaoAlertaRepositoryPort {

    private final ConfiguracaoAlertaRepository repository;

    @Override
    @Transactional(readOnly = true)
    public Optional<Integer> findToleranciaMinutos(Long cuidadorId) {
        return repository.findById(cuidadorId).map(ConfiguracaoAlertaEntity::getToleranciaMinutos);
    }

    @Override
    @Transactional
    public void salvarToleranciaMinutos(Long cuidadorId, int toleranciaMinutos) {
        repository.salvarToleranciaMinutos(cuidadorId, toleranciaMinutos);
    }
}
