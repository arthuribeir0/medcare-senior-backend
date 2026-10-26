package com.medcare.senior.domain.medicamento;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Todas as leituras ignoram medicamentos removidos logicamente: para a aplicação, removido = inexistente.
 */
public interface MedicamentoRepositoryPort {

    Medicamento save(Medicamento medicamento);

    Optional<Medicamento> findById(Long id);

    List<Medicamento> findAllByPacienteId(Long pacienteId);

    boolean existsById(Long id);

    /**
     * Remoção lógica: preenche removido_em. Não apaga a linha, para preservar o histórico de doses (RN-008).
     */
    void marcarComoRemovido(Long id, LocalDateTime agora);

    /**
     * Remove FISICAMENTE todos os medicamentos dos pacientes do cuidador, inclusive os já removidos logicamente
     * (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
