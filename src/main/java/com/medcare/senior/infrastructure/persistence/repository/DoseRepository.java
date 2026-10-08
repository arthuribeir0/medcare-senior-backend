package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Os deletes abaixo recebem o status como parâmetro e são sempre chamados com PENDENTE pelo adapter:
 * doses registradas nunca são apagadas por eles.
 */
@Repository
public interface DoseRepository extends JpaRepository<DoseEntity, Long> {

    @Query("SELECT d.horarioProgramado FROM DoseEntity d "
            + "WHERE d.tratamento.id = :tratamentoId AND d.horarioProgramado >= :desde")
    List<LocalDateTime> findHorariosProgramadosDesde(@Param("tratamentoId") Long tratamentoId,
                                                     @Param("desde") LocalDateTime desde);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DoseEntity d WHERE d.tratamento.id = :tratamentoId "
            + "AND d.status = :status AND d.horarioProgramado > :agora")
    int deleteFuturasByTratamentoIdAndStatus(@Param("tratamentoId") Long tratamentoId,
                                             @Param("status") StatusDose status,
                                             @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DoseEntity d WHERE d.status = :status AND d.horarioProgramado > :agora "
            + "AND d.tratamento.id IN (SELECT t.id FROM TratamentoEntity t WHERE t.medicamento.id = :medicamentoId)")
    int deleteFuturasByMedicamentoIdAndStatus(@Param("medicamentoId") Long medicamentoId,
                                              @Param("status") StatusDose status,
                                              @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DoseEntity d WHERE d.status = :status AND d.tratamento.id IN ("
            + "SELECT t.id FROM TratamentoEntity t WHERE t.medicamento.idoso.cuidador.id = :cuidadorId)")
    int deleteByCuidadorIdAndStatus(@Param("cuidadorId") Long cuidadorId, @Param("status") StatusDose status);
}
