package com.medcare.senior.domain.autenticacao;

import com.medcare.senior.domain.usuario.Role;

public interface TokenGeneratorPort {

    String generateToken(Long userId, Role role);
}
