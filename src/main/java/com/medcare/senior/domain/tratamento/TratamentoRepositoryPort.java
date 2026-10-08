package com.medcare.senior.domain.tratamento;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Todas as leituras ignoram tratamentos removidos logicamente: para a aplicação, removido = inexistente.
 */
public interface TratamentoRepositoryPort {

    Tratamento save(Tratamento tratamento);

    List<Tratamento> findAllByMedicamentoId(Long medicamentoId);

    /**
     * Busca o tratamento somente se ele pertencer ao cuidador, pelo caminho
     * tratamento -> medicamento -> idoso -> cuidador. Tratamento de outro cuidador, ou removido (ele ou o
     * medicamento), volta vazio, igual a inexistente.
     */
    Optional<Tratamento> findByIdAndCuidadorId(Long id, Long cuidadorId);

    /**
     * Tratamentos ativos em {@code hoje}: não removidos (nem o medicamento) e sem data de término ou terminando
     * hoje ou depois. Usado pelo job de reabastecimento de doses.
     */
    List<Tratamento> findAllAtivos(LocalDate hoje);

    /**
     * Remoção lógica de todos os tratamentos (ainda não removidos) do medicamento.
     */
    void marcarComoRemovidosPorMedicamento(Long medicamentoId, LocalDateTime agora);

    /**
     * Remove FISICAMENTE todos os tratamentos dos medicamentos dos idosos do cuidador, inclusive os já removidos
     * logicamente (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
