package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.usuario.Role;
import org.springframework.beans.factory.annotation.Value;
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
     *   [ ] Use hasRole(CUIDADOR) / hasRole(PACIENTE) — nunca apenas authenticated(), que aceita qualquer papel. Rota
     *       dos dois papéis: hasAnyRole com os dois nomes, e o use case decide o acesso de cada papel.
     *   [ ] permitAll() só para pontos de entrada públicos (cadastro, login, pareamento). Endpoint público que
     *       aceita tentativas (códigos, senhas) deve ter rate limiting.
     *   [ ] No controller, use @AuthenticationPrincipal AuthenticatedUser e cuidadorId()/pacienteId(),
     *       nunca o userId solto.
     * =====================================================================================================
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider,
                                                   @Value("${app.docs.enabled:false}") boolean docsEnabled) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth
                            .requestMatchers(HttpMethod.POST, "/api/cuidadores").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                            .requestMatchers(HttpMethod.POST, "/api/vinculo/validar").permitAll()
                            .requestMatchers("/error").permitAll()
                            // Saúde mínima para o HEALTHCHECK do contêiner: só {"status":"UP"}, sem detalhes
                            .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                            .requestMatchers(HttpMethod.GET, "/api/cuidadores/me").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.DELETE, "/api/cuidadores/me").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.PUT, "/api/cuidadores/me/fcm-token").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.DELETE, "/api/cuidadores/me/fcm-token").hasRole(Role.CUIDADOR.name())
                            // Rotas do aparelho do paciente: ANTES de /api/pacientes/** (a primeira regra que casa vence)
                            .requestMatchers(HttpMethod.GET, "/api/pacientes/me", "/api/pacientes/me/doses").hasRole(Role.PACIENTE.name())
                            .requestMatchers("/api/pacientes/**").hasRole(Role.CUIDADOR.name())
                            // Confirmação de dose: o paciente (a própria) ou o cuidador (dos pacientes dele); o dono fica no use case
                            .requestMatchers(HttpMethod.PUT, "/api/doses/*/confirmar").hasAnyRole(Role.PACIENTE.name(), Role.CUIDADOR.name())
                            .requestMatchers("/api/medicamentos/**").hasRole(Role.CUIDADOR.name())
                            .requestMatchers("/api/tratamentos/**").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.GET, "/api/configuracoes/tolerancia").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.PUT, "/api/configuracoes/tolerancia").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.GET, "/api/relatorios/diario").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.GET, "/api/relatorios/pdf").hasRole(Role.CUIDADOR.name())
                            .requestMatchers(HttpMethod.GET, "/api/historico").hasRole(Role.CUIDADOR.name())
                            // Mensagens (UC-010): os dois papéis; de quem é a conversa fica no AcessoAConversaService
                            .requestMatchers(HttpMethod.POST, "/api/mensagens").hasAnyRole(Role.CUIDADOR.name(), Role.PACIENTE.name())
                            .requestMatchers(HttpMethod.GET, "/api/mensagens", "/api/mensagens/conversas", "/api/mensagens/nao-lidas")
                            .hasAnyRole(Role.CUIDADOR.name(), Role.PACIENTE.name())
                            .requestMatchers(HttpMethod.PUT, "/api/mensagens/lidas").hasAnyRole(Role.CUIDADOR.name(), Role.PACIENTE.name());

                    // Documentação OpenAPI/Swagger: liberada SOMENTE com app.docs.enabled=true (DOCS_ENABLED).
                    // Desligada, essas rotas caem no denyAll() abaixo, como qualquer outra rota sem regra.
                    if (docsEnabled) {
                        auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
                    }

                    // Fail closed: qualquer rota sem regra explícita acima é bloqueada (ver checklist)
                    auth.anyRequest().denyAll();
                })
                // Sem token (ou token inválido) -> 401; token válido sem o papel exigido -> 403
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
