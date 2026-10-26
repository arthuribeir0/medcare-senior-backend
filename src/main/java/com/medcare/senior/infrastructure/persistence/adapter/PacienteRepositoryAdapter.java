package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.exception.PacienteJaCadastradoException;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.infrastructure.persistence.mapper.PacienteMapper;
import com.medcare.senior.infrastructure.persistence.repository.PacienteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PacienteRepositoryAdapter implements PacienteRepositoryPort {

    private final PacienteRepository pacienteRepository;
    private final PacienteMapper pacienteMapper;

    @Override
    public Paciente save(Paciente paciente) {
        try {
            return pacienteMapper.toDomain(pacienteRepository.save(pacienteMapper.toEntity(paciente)));
        } catch (DataIntegrityViolationException e) {
            // Cadastro concorrente duplicado: o índice único do banco garante a RN-003
            throw new PacienteJaCadastradoException();
        }
    }

    @Override
    public boolean existsByCuidadorIdAndNomeAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento) {
        return pacienteRepository.existsByCuidador_IdAndNomeIgnoreCaseAndDataNascimento(cuidadorId, nome, dataNascimento);
    }

    @Override
    public Optional<Paciente> findByIdAndCuidadorId(Long id, Long cuidadorId) {
        return pacienteRepository.findByIdAndCuidador_Id(id, cuidadorId).map(pacienteMapper::toDomain);
    }

    @Override
    public Optional<Paciente> findById(Long id) {
        return pacienteRepository.findById(id).map(pacienteMapper::toDomain);
    }

    @Override
    public List<Paciente> findAllByCuidadorId(Long cuidadorId) {
        return pacienteRepository.findAllByCuidadorIdOrderByNome(cuidadorId).stream()
                .map(pacienteMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional
    public void deleteAllByCuidadorId(Long cuidadorId) {
        pacienteRepository.deleteAllByCuidadorId(cuidadorId);
    }
}
