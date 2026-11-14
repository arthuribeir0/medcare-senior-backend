/*
 * Implementa a porta de hash de senha com BCrypt (RN-002 e RNF-03): a senha nunca é guardada em texto claro. O
 * BCrypt é lento de propósito e já inclui sal.
 */
package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BCryptPasswordHasherAdapter implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String hash(String senha) {
        return passwordEncoder.encode(senha);
    }

    @Override
    public boolean matches(String senha, String hash) {
        return passwordEncoder.matches(senha, hash);
    }
}
