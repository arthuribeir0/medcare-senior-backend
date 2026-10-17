package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.exception.IdosoJaCadastradoException;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.IdosoMapper;
import com.medcare.senior.infrastructure.persistence.repository.IdosoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdosoRepositoryAdapter implements IdosoRepositoryPort {

    private final IdosoRepository idosoRepository;
    private final IdosoMapper idosoMapper;

    @Override
    public Idoso save(Idoso idoso) {
        try {
            return idosoMapper.toDomain(idosoRepository.save(idosoMapper.toEntity(idoso)));
        } catch (DataIntegrityViolationException e) {
            // Cadastro concorrente duplicado: o índice único do banco garante a RN-003
            throw new IdosoJaCadastradoException();
        }
    }

    @Override
    public boolean existsByCuidadorIdAndNomeAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento) {
        return idosoRepository.existsByCuidador_IdAndNomeIgnoreCaseAndDataNascimento(cuidadorId, nome, dataNascimento);
    }

    @Override
    public Optional<Idoso> findByIdAndCuidadorId(Long id, Long cuidadorId) {
        return idosoRepository.findByIdAndCuidador_Id(id, cuidadorId).map(idosoMapper::toDomain);
    }

    @Override
    public List<Idoso> findAllByCuidadorId(Long cuidadorId) {
        return idosoRepository.findAllByCuidadorIdOrderByNome(cuidadorId).stream()
                .map(idosoMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllByCuidadorId(Long cuidadorId) {
        idosoRepository.deleteAllByCuidadorId(cuidadorId);
    }
}
