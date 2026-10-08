package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.CodigoVinculoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CodigoVinculoRepository extends JpaRepository<CodigoVinculoEntity, Long> {

    /**
     * Indica se já existe um código ativo (não usado e não expirado) com esse valor.
     */
    boolean existsByCodigoUnicoAndUsadoFalseAndExpiraEmAfter(String codigoUnico, LocalDateTime agora);

    Optional<CodigoVinculoEntity> findFirstByCodigoUnicoOrderByIdDesc(String codigoUnico);

    @Modifying
    @Query("UPDATE CodigoVinculoEntity c SET c.usado = true "
            + "WHERE c.id = :id AND c.usado = false AND c.expiraEm > :agora")
    int marcarComoUsado(@Param("id") Long id, @Param("agora") LocalDateTime agora);

    // Inclui também códigos dos idosos do cuidador, por segurança, caso cuidador_id e idoso.cuidador_id divirjam
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM CodigoVinculoEntity c WHERE c.cuidador.id = :cuidadorId "
            + "OR c.idoso.id IN (SELECT i.id FROM IdosoEntity i WHERE i.cuidador.id = :cuidadorId)")
    void deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
