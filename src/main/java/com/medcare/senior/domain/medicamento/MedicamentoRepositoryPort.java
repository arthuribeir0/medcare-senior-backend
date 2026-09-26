package com.medcare.senior.domain.medicamento;

import java.util.List;
import java.util.Optional;

public interface MedicamentoRepositoryPort {

    Medicamento save(Medicamento medicamento);

    Optional<Medicamento> findById(Long id);

    List<Medicamento> findAllByIdosoId(Long idosoId);

    void deleteById(Long id);

    boolean existsById(Long id);
}
