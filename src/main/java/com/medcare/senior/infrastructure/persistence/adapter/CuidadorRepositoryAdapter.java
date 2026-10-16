package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.exception.EmailJaCadastradoException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.CuidadorMapper;
import com.medcare.senior.infrastructure.persistence.repository.CuidadorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CuidadorRepositoryAdapter implements CuidadorRepositoryPort {

    private final CuidadorRepository cuidadorRepository;
    private final CuidadorMapper cuidadorMapper;

    @Override
    public Cuidador save(Cuidador cuidador) {
        try {
            return cuidadorMapper.toDomain(cuidadorRepository.save(cuidadorMapper.toEntity(cuidador)));
        } catch (DataIntegrityViolationException e) {
            // Cadastro concorrente com o mesmo e-mail: a constraint UNIQUE do banco garante a RN-001
            throw new EmailJaCadastradoException(cuidador.getEmail());
        }
    }

    @Override
    public Optional<Cuidador> findByEmail(String email) {
        return cuidadorRepository.findByEmail(email).map(cuidadorMapper::toDomain);
    }

    @Override
    public boolean existsByEmail(String email) {
        return cuidadorRepository.existsByEmail(email);
    }

    @Override
    public boolean existsById(Long id) {
        return cuidadorRepository.existsById(id);
    }

    @Override
    public Optional<Cuidador> findById(Long id) {
        return cuidadorRepository.findById(id).map(cuidadorMapper::toDomain);
    }

    @Override
    @Transactional
    public boolean atualizarFcmToken(Long cuidadorId, String fcmToken) {
        return cuidadorRepository.atualizarFcmToken(cuidadorId, fcmToken) > 0;
    }

    @Override
    @Transactional
    public void removerFcmTokenDosOutrosCuidadores(String fcmToken, Long cuidadorId) {
        cuidadorRepository.removerFcmTokenDosOutros(fcmToken, cuidadorId);
    }

    @Override
    @Transactional
    public void removerFcmTokenSeIgual(Long cuidadorId, String fcmToken) {
        cuidadorRepository.removerFcmTokenSeIgual(cuidadorId, fcmToken);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        cuidadorRepository.deleteById(id);
    }
}
