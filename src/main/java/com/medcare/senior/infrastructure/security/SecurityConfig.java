package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.usuario.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /*
     * =====================================================================================================
     *  CHECKLIST DE AUTORIZAÇÃO — LEIA ANTES DE CRIAR UM NOVO ENDPOINT
     * =====================================================================================================
     *  A política é "fail closed": a regra padrão (anyRequest) é denyAll(). Endpoint sem regra explícita
     *  aqui responde 403 para TODO MUNDO, inclusive com token válido.
     *
     *  Ao criar um novo endpoint:
     *   [ ] Adicione aqui uma regra explícita .requestMatchers(...).hasRole(...) ou .permitAll().
     *       Não confie na regra padrão, que agora bloqueia por padrão.
     *   [ ] Declare a regra ANTES de anyRequest(): as regras são avaliadas em ordem e a primeira que casar vence.
     *   [ ] Use hasRole(CUIDADOR) / hasRole(IDOSO) — nunca apenas authenticated(), que aceita qualquer papel.
     *   [ ] permitAll() só para pontos de entrada públicos (cadastro, login, pareamento). Endpoint público que
     *       aceita tentativas (códigos, senhas) deve ter rate limiting.
     *   [ ] No controller, use @AuthenticationPrincipal AuthenticatedUser e cuidadorId()/idosoId(),
     *       nunca o userId solto.
     * =====================================================================================================
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/cuidadores").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/vinculo/validar").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/idosos/**").hasRole(Role.CUIDADOR.name())
                        .requestMatchers("/api/medicamentos/**").hasRole(Role.CUIDADOR.name())
                        // Fail closed: qualquer rota sem regra explícita acima é bloqueada (ver checklist)
                        .anyRequest().denyAll()
                )
                // Sem token (ou token inválido) -> 401; token válido sem o papel exigido -> 403
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
