package com.medcare.senior.infrastructure.security;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Token de PACIENTE ainda válido, mas de um aparelho desconectado (pedido autorizado pelo cuidador ou desconexão feita
 * por ele) ou de um paciente que não existe mais, responde 401 SESSAO_INVALIDA em qualquer rota, antes do controller:
 * o app limpa a sessão e volta à tela inicial. Par do {@link SessaoDoCuidadorInterceptor}.
 *
 * <p>Custo: uma consulta por chave primária por requisição do paciente.
 */
@Component
public class SessaoDoPacienteInterceptor implements HandlerInterceptor {

    private final PacienteRepositoryPort pacienteRepository;

    public SessaoDoPacienteInterceptor(PacienteRepositoryPort pacienteRepository) {
        this.pacienteRepository = pacienteRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao != null && autenticacao.getPrincipal() instanceof AuthenticatedUser usuario && usuario.isPaciente()) {
            Instant emitidoEm = request.getAttribute(JwtAuthenticationFilter.ATRIBUTO_TOKEN_EMITIDO_EM) instanceof Instant instante
                    ? instante : Instant.EPOCH;
            if (!pacienteRepository.sessaoValida(usuario.userId(), emitidoEm)) {
                throw new SessaoInvalidaException();
            }
        }
        return true;
    }
}
