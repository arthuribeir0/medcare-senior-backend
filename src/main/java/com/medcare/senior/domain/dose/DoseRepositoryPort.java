package com.medcare.senior.domain.dose;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Doses registradas são imutáveis (RN-008): a única atualização possível é a transição a partir de PENDENTE
 * ({@link #registrarSePendente}), e as únicas remoções atingem SOMENTE doses PENDENTES.
 */
public interface DoseRepositoryPort {

    List<Dose> saveAll(List<Dose> doses);

    /**
     * Horários que já têm dose (de qualquer status) para o tratamento, a partir de {@code desde} (inclusive).
     * Usado para a geração ser idempotente.
     */
    Set<LocalDateTime> findHorariosProgramadosByTratamentoIdDesde(Long tratamentoId, LocalDateTime desde);

    /**
     * Até {@code limite} doses PENDENTES com horário programado igual ou anterior a {@code vencidasAte}, das mais
     * antigas para as mais novas. Ignora doses anonimizadas (sem tratamento) e doses de tratamento ou medicamento
     * removido logicamente.
     */
    List<DoseVencida> findPendentesVencidas(LocalDateTime vencidasAte, int limite);

    /**
     * Doses do idoso com horário programado em [{@code inicio}, {@code fim}), com idoso e medicamento, em uma única
     * consulta, ordenadas por horário. Ignora doses anonimizadas (sem tratamento) e de tratamento ou medicamento
     * removido logicamente.
     */
    List<DoseDetalhada> findDetalhadasDoIdosoEntre(Long idosoId, LocalDateTime inicio, LocalDateTime fim);

    /**
     * A dose, SOMENTE se pertencer ao idoso (dose -> tratamento -> medicamento -> idoso) e o tratamento e o
     * medicamento não estiverem removidos. Dose de outro idoso, anonimizada ou de item removido volta vazia.
     */
    Optional<DoseDetalhada> findDetalhadaByIdAndIdosoId(Long doseId, Long idosoId);

    /**
     * Grava a versão registrada da dose (status e hora de confirmação) SOMENTE se no banco ela ainda estiver
     * PENDENTE, em um único UPDATE atômico ({@code ... WHERE id = ? AND status = 'PENDENTE'}). Se outra transação
     * registrou a dose antes (ex.: confirmação do idoso), nada muda.
     *
     * @param doseRegistrada dose já em um status registrado, criada por uma transição de {@link Dose}
     * @return quantas linhas mudaram: 1 se a transição aconteceu, 0 se a dose não existe ou não estava PENDENTE
     * @throws IllegalArgumentException se {@code doseRegistrada} ainda estiver PENDENTE ou não tiver id
     */
    int registrarSePendente(Dose doseRegistrada);

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
