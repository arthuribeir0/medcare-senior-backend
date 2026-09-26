package com.medcare.senior.domain.autenticacao;

import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.usuario.Role;

/**
 * Usuário autenticado a partir do JWT (principal do Spring Security, preenchido pelo JwtAuthenticationFilter).
 *
 * <p>O {@code userId} sozinho é ambíguo: cuidador id=1 e idoso id=1 são pessoas diferentes. Por isso, nos
 * controllers, prefira {@link #cuidadorId()} / {@link #idosoId()}, que só devolvem o ID se o papel for o
 * esperado, em vez de ler {@code userId} diretamente.
 */
public record AuthenticatedUser(Long userId, Role role) {

    public boolean isCuidador() {
        return role == Role.CUIDADOR;
    }

    public boolean isIdoso() {
        return role == Role.IDOSO;
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
     * @throws AcessoNegadoException se o usuário autenticado não for um idoso
     */
    public Long idosoId() {
        if (!isIdoso()) {
            throw new AcessoNegadoException();
        }
        return userId;
    }
}
