package com.medcare.senior.domain.autenticacao;

import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.usuario.Role;

/**
 * Usuário autenticado a partir do JWT (principal do Spring Security, preenchido pelo JwtAuthenticationFilter).
 *
 * <p>O {@code userId} sozinho é ambíguo: cuidador id=1 e paciente id=1 são pessoas diferentes. Por isso, nos
 * controllers, prefira {@link #cuidadorId()} / {@link #pacienteId()}, que só devolvem o ID se o papel for o
 * esperado, em vez de ler {@code userId} diretamente.
 */
public record AuthenticatedUser(Long userId, Role role) {

    public boolean isCuidador() {
        return role == Role.CUIDADOR;
    }

    public boolean isPaciente() {
        return role == Role.PACIENTE;
    }

    /**
     * @throws AcessoNegadoException se o usuário autenticado não for um cuidador
     */
    public Long cuidadorId() {
        if (!isCuidador()) {
            throw new AcessoNegadoException();
        }
        return userId;
    }

    /**
     * @throws AcessoNegadoException se o usuário autenticado não for um paciente
     */
    public Long pacienteId() {
        if (!isPaciente()) {
            throw new AcessoNegadoException();
        }
        return userId;
    }
}
