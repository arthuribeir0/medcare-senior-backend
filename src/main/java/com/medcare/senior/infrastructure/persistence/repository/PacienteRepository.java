package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.PacienteEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PacienteRepository extends JpaRepository<PacienteEntity, Long> {

    boolean existsByCuidador_IdAndNomeIgnoreCaseAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento);

    Optional<PacienteEntity> findByIdAndCuidador_Id(Long id, Long cuidadorId);

    // Ordem por nome sem diferenciar maiúsculas de minúsculas; o id desempata nomes iguais
    @Query("SELECT i FROM PacienteEntity i WHERE i.cuidador.id = :cuidadorId ORDER BY LOWER(i.nome), i.id")
    List<PacienteEntity> findAllByCuidadorIdOrderByNome(@Param("cuidadorId") Long cuidadorId);

    // sessao_valida_desde fica fora da entidade de propósito: só estes dois comandos a leem e gravam, e um save() do
    // paciente nunca a sobrescreve
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE paciente SET sessao_valida_desde = :desde WHERE id = :id", nativeQuery = true)
    int invalidarSessoes(@Param("id") Long id, @Param("desde") OffsetDateTime desde);

    @Query(value = "SELECT count(*) FROM paciente WHERE id = :id "
            + "AND (sessao_valida_desde IS NULL OR sessao_valida_desde <= :emitidoEm)", nativeQuery = true)
    long contarComSessaoValida(@Param("id") Long id, @Param("emitidoEm") OffsetDateTime emitidoEm);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PacienteEntity i WHERE i.cuidador.id = :cuidadorId")
    void deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
