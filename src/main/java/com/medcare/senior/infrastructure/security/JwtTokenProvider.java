/*
 * Emite e valida o JWT (HS256, biblioteca JJWT) com o ID, o papel e a data de emissão. O cuidador expira em 24
 * horas e o paciente em 180 dias (ambos configuráveis). Aceita o nome antigo do papel (IDOSO) para tokens emitidos
 * antes da renomeação.
 */
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
import java.time.Instant;
import java.util.Date;

@Component
public class JwtTokenProvider implements TokenGeneratorPort {

    private static final String ROLE_CLAIM = "role";
    private static final String LEGACY_ROLE_PACIENTE = "IDOSO";

    private final SecretKey secretKey;
    private final long expirationCuidadorMs;
    private final long expirationPacienteMs;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-ms}") long expirationCuidadorMs,
                            @Value("${jwt.expiration-paciente-ms}") long expirationPacienteMs) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationCuidadorMs = expirationCuidadorMs;
        this.expirationPacienteMs = expirationPacienteMs;
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
            case PACIENTE -> expirationPacienteMs;
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
        String role = parseClaims(token).get(ROLE_CLAIM, String.class);
        // Tokens emitidos antes da troca de nome do papel (valem 180 dias): o aparelho continua pareado
        return LEGACY_ROLE_PACIENTE.equals(role) ? Role.PACIENTE : Role.valueOf(role);
    }

    /**
     * Quando o token foi emitido (precisão de segundo, como no JWT); {@link Instant#EPOCH} se faltar o campo, para um
     * token sem data nunca sobreviver a uma desconexão.
     */
    public Instant getEmitidoEm(String token) {
        Date emitidoEm = parseClaims(token).getIssuedAt();
        return emitidoEm == null ? Instant.EPOCH : emitidoEm.toInstant();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
