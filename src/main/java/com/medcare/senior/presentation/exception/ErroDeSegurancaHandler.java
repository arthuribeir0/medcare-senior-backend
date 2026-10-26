package com.medcare.senior.presentation.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Respostas do Spring Security (antes de chegar ao controller) no mesmo {@link ErroResponse} do resto da API:
 * sem token ou com token inválido/expirado, 401 NAO_AUTENTICADO; token válido sem o papel exigido (ou rota fora da
 * lista do SecurityConfig), 403 ACESSO_NEGADO. Sem isso o 401 saía sem corpo e o 403 com o JSON padrão do Spring.
 */
@Component
public class ErroDeSegurancaHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public ErroDeSegurancaHandler(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        escrever(response, HttpStatus.UNAUTHORIZED, CodigoErro.NAO_AUTENTICADO,
                "Autenticação necessária. Envie um token válido no cabeçalho Authorization.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO, "Acesso negado para este perfil.");
    }

    private void escrever(HttpServletResponse response, HttpStatus status, CodigoErro codigo, String mensagem)
            throws IOException {
        ErroResponse corpo = new ErroResponse(LocalDateTime.now(), status.value(), status.getReasonPhrase(), codigo.name(),
                mensagem, List.of());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), corpo);
    }
}
