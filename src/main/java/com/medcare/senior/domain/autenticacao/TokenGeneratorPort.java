/*
 * Porta (interface) de emissão de token: o domínio pede um token para um usuário e um papel, sem saber que é JWT.
 */
package com.medcare.senior.domain.autenticacao;

import com.medcare.senior.domain.usuario.Role;

public interface TokenGeneratorPort {

    String generateToken(Long userId, Role role);
}
