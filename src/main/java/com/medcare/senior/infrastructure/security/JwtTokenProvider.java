package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.usuario.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtTokenProvider implements TokenGeneratorPort {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey secretKey;
    private final long expirationCuidadorMs;
    private final long expirationIdosoMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-ms}") long expirationCuidadorMs,
                            @Value("${jwt.expiration-idoso-ms}") long expirationIdosoMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationCuidadorMs = expirationCuidadorMs;
        this.expirationIdosoMs = expirationIdosoMs;
    }

    @Override
    public String generateToken(Long userId, Role role) {
        Date agora = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(agora)
                .expiration(new Date(agora.getTime() + validadeMs(role)))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    private long validadeMs(Role role) {
        // switch exaustivo: um papel novo no enum obriga a definir aqui a validade do token dele
        return switch (role) {
            case CUIDADOR -> expirationCuidadorMs;
            case IDOSO -> expirationIdosoMs;
        };
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Long getUserId(String token) {
        return Long.valueOf(parseClaims(token).getSubject());
    }

    public Role getRole(String token) {
        return Role.valueOf(parseClaims(token).get(ROLE_CLAIM, String.class));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
