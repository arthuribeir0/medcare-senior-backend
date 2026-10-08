package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Não há operação de atualização de dose: doses registradas são imutáveis (RN-008). As únicas remoções
 * possíveis atingem SOMENTE doses PENDENTES.
 */
public interface DoseRepositoryPort {

    List<Dose> saveAll(List<Dose> doses);

    /**
     * Horários que já têm dose (de qualquer status) para o tratamento, a partir de {@code desde} (inclusive).
     * Usado para a geração ser idempotente.
     */
    Set<LocalDateTime> findHorariosProgramadosByTratamentoIdDesde(Long tratamentoId, LocalDateTime desde);

    /**
     * Apaga as doses PENDENTES do tratamento com horário programado depois de {@code agora}.
     * Doses registradas e doses já passadas não são tocadas.
     */
    void deletePendentesFuturasByTratamentoId(Long tratamentoId, LocalDateTime agora);

    /**
     * Apaga as doses PENDENTES futuras de todos os tratamentos do medicamento (remoção lógica do medicamento).
     */
    void deletePendentesFuturasByMedicamentoId(Long medicamentoId, LocalDateTime agora);

    /**
     * Apaga TODAS as doses PENDENTES (passadas e futuras) dos tratamentos do cuidador (exclusão de conta, LGPD).
     * As registradas ficam e são anonimizadas pelo banco (ON DELETE SET NULL) quando os tratamentos são apagados.
     */
    void deletePendentesByCuidadorId(Long cuidadorId);
}
