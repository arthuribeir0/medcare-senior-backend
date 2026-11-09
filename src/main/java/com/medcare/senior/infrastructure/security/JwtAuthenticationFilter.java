package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Não é um @Component de propósito: é instanciado no SecurityConfig para não ser registrado
 * uma segunda vez como filtro do servlet container pelo Spring Boot.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Atributo da requisição com o "emitido em" do token autenticado (Instant), lido pelo SessaoDoPacienteInterceptor. */
    public static final String ATRIBUTO_TOKEN_EMITIDO_EM = "medcare.tokenEmitidoEm";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            if (jwtTokenProvider.validateToken(token)) {
                autenticar(token, request);
            }
        }

        filterChain.doFilter(request, response);
    }

    private void autenticar(String token, HttpServletRequest request) {
        try {
            Long userId = jwtTokenProvider.getUserId(token);
            Role role = jwtTokenProvider.getRole(token);
            request.setAttribute(ATRIBUTO_TOKEN_EMITIDO_EM, jwtTokenProvider.getEmitidoEm(token));

            // O principal carrega ID + papel, recuperado nos controllers via @AuthenticationPrincipal AuthenticatedUser
            AuthenticatedUser principal = new AuthenticatedUser(userId, role);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException e) {
            // Token assinado mas com claims inválidas: segue sem autenticação
            SecurityContextHolder.clearContext();
        }
    }
}
