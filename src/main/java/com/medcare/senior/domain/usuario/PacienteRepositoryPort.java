package com.medcare.senior.domain.usuario;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PacienteRepositoryPort {

    /**
     * @throws com.medcare.senior.domain.exception.PacienteJaCadastradoException se violar a RN-003
     */
    Paciente save(Paciente paciente);

    /**
     * Comparação do nome sem diferenciar maiúsculas de minúsculas (RN-003).
     */
    boolean existsByCuidadorIdAndNomeAndDataNascimento(Long cuidadorId, String nome, LocalDate dataNascimento);

    Optional<Paciente> findByIdAndCuidadorId(Long id, Long cuidadorId);

    /**
     * Busca sem filtro de dono: usar SOMENTE com o ID do próprio paciente autenticado (token PACIENTE).
     */
    Optional<Paciente> findById(Long id);

    /**
     * Pacientes do cuidador, em ordem alfabética de nome sem diferenciar maiúsculas de minúsculas.
     */
    List<Paciente> findAllByCuidadorId(Long cuidadorId);

    /**
     * Remove todos os pacientes do cuidador (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
