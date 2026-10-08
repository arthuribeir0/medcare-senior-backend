package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MedicamentoRepository extends JpaRepository<MedicamentoEntity, Long> {

    Optional<MedicamentoEntity> findByIdAndRemovidoEmIsNull(Long id);

    List<MedicamentoEntity> findAllByIdoso_IdAndRemovidoEmIsNullOrderByNomeAsc(Long idosoId);

    boolean existsByIdAndRemovidoEmIsNull(Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MedicamentoEntity m SET m.removidoEm = :agora WHERE m.id = :id AND m.removidoEm IS NULL")
    int marcarComoRemovido(@Param("id") Long id, @Param("agora") LocalDateTime agora);

    // Exclusão física (LGPD): inclui os medicamentos já removidos logicamente
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MedicamentoEntity m "
            + "WHERE m.idoso.id IN (SELECT i.id FROM IdosoEntity i WHERE i.cuidador.id = :cuidadorId)")
    void deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
