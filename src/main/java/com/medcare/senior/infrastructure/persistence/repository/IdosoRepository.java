package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.IdosoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface IdosoRepository extends JpaRepository<IdosoEntity, Long> {

    boolean existsByCuidador_IdAndNomeIgnoreCaseAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento);

    Optional<IdosoEntity> findByIdAndCuidador_Id(Long id, Long cuidadorId);
}
