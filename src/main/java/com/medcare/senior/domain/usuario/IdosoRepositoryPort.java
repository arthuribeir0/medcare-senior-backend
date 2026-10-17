package com.medcare.senior.domain.usuario;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IdosoRepositoryPort {

    /**
     * @throws com.medcare.senior.domain.exception.IdosoJaCadastradoException se violar a RN-003
     */
    Idoso save(Idoso idoso);

    /**
     * Comparação do nome sem diferenciar maiúsculas de minúsculas (RN-003).
     */
    boolean existsByCuidadorIdAndNomeAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento);

    Optional<Idoso> findByIdAndCuidadorId(Long id, Long cuidadorId);

    /**
     * Idosos do cuidador, em ordem alfabética de nome sem diferenciar maiúsculas de minúsculas.
     */
    List<Idoso> findAllByCuidadorId(Long cuidadorId);

    /**
     * Remove todos os idosos do cuidador (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
