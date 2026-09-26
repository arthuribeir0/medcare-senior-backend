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
