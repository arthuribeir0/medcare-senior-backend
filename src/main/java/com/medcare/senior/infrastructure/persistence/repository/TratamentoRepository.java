package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.TratamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TratamentoRepository extends JpaRepository<TratamentoEntity, Long> {

    List<TratamentoEntity> findAllByMedicamento_IdAndRemovidoEmIsNullOrderByDataInicioAscIdAsc(Long medicamentoId);

    // Posse: tratamento -> medicamento -> paciente -> cuidador; nem o tratamento nem o medicamento podem estar removidos
    @Query("SELECT t FROM TratamentoEntity t WHERE t.id = :id AND t.removidoEm IS NULL "
            + "AND t.medicamento.removidoEm IS NULL AND t.medicamento.paciente.cuidador.id = :cuidadorId")
    Optional<TratamentoEntity> findAtivoByIdAndCuidadorId(@Param("id") Long id, @Param("cuidadorId") Long cuidadorId);

    @Query("SELECT t FROM TratamentoEntity t WHERE t.removidoEm IS NULL AND t.medicamento.removidoEm IS NULL "
            + "AND (t.dataFim IS NULL OR t.dataFim >= :hoje) ORDER BY t.id")
    List<TratamentoEntity> findAllAtivos(@Param("hoje") LocalDate hoje);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TratamentoEntity t SET t.removidoEm = :agora "
            + "WHERE t.medicamento.id = :medicamentoId AND t.removidoEm IS NULL")
    int marcarComoRemovidosPorMedicamento(@Param("medicamentoId") Long medicamentoId,
                                          @Param("agora") LocalDateTime agora);

    // Exclusão física (LGPD): inclui os tratamentos já removidos logicamente
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM TratamentoEntity t WHERE t.medicamento.id IN ("
            + "SELECT m.id FROM MedicamentoEntity m WHERE m.paciente.cuidador.id = :cuidadorId)")
    void deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
