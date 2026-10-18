package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.IdosoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface IdosoRepository extends JpaRepository<IdosoEntity, Long> {

    boolean existsByCuidador_IdAndNomeIgnoreCaseAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento);

    Optional<IdosoEntity> findByIdAndCuidador_Id(Long id, Long cuidadorId);

    // Ordem por nome sem diferenciar maiúsculas de minúsculas; o id desempata nomes iguais
    @Query("SELECT i FROM IdosoEntity i WHERE i.cuidador.id = :cuidadorId ORDER BY LOWER(i.nome), i.id")
    List<IdosoEntity> findAllByCuidadorIdOrderByNome(@Param("cuidadorId") Long cuidadorId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM IdosoEntity i WHERE i.cuidador.id = :cuidadorId")
    void deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
