package com.medcare.senior.domain.usuario;

import java.util.Optional;

public interface CuidadorRepositoryPort {

    /**
     * @throws com.medcare.senior.domain.exception.EmailJaCadastradoException se o e-mail já estiver em uso (RN-001)
     */
    Cuidador save(Cuidador cuidador);

    Optional<Cuidador> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsById(Long id);
}
