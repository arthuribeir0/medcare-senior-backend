package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.infrastructure.persistence.entity.MedicamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicamentoRepository extends JpaRepository<MedicamentoEntity, Long> {

    List<MedicamentoEntity> findAllByIdoso_IdOrderByNomeAsc(Long idosoId);
}
