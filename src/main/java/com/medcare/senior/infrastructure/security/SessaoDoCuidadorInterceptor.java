package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Token de CUIDADOR ainda válido de uma conta que não existe mais (excluída) responde 401 SESSAO_INVALIDA em
 * qualquer rota, antes do controller, para o app descartar a sessão e voltar ao login. Sem isto, cada rota
 * responderia de um jeito (404, lista vazia...). O 404 continua valendo para recursos de outro dono ou inexistentes.
 *
 * <p>Roda depois do Spring Security (só para requisições já autorizadas). A exceção é convertida pelo
 * GlobalExceptionHandler, como as dos controllers. Custo: uma consulta por chave primária por requisição do cuidador.
 */
@Component
public class SessaoDoCuidadorInterceptor implements HandlerInterceptor {

    private final CuidadorRepositoryPort cuidadorRepository;

    public SessaoDoCuidadorInterceptor(CuidadorRepositoryPort cuidadorRepository) {
        this.cuidadorRepository = cuidadorRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof AuthenticatedUser usuario
                && usuario.isCuidador() && !cuidadorRepository.existsById(usuario.userId())) {
            throw SessaoInvalidaException.doCuidador();
        }
        return true;
    }
}
