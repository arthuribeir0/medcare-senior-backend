package com.medcare.senior.application.service;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * De quem é a conversa (UC-010) e de que lado está quem chama. A conversa é sempre de um idoso com o seu cuidador.
 *
 * <ul>
 *   <li>Cuidador: informa o idoso, que precisa ser dele (idoso alheio responde 404, como inexistente). A conta
 *       excluída já é barrada antes, com 401 SESSAO_INVALIDA, pelo SessaoDoCuidadorInterceptor.</li>
 *   <li>Idoso: a conversa é a dele (o idoso vem do token). Informar outro idoso é 403 ACESSO_NEGADO; idoso que não
 *       existe mais é 401 SESSAO_INVALIDA.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcessoAConversaService {

    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param idosoId idoso informado na requisição (obrigatório para o cuidador; opcional para o idoso)
     * @throws ParametroInvalidoException    se o cuidador não informar o idoso
     * @throws RecursoNaoEncontradoException se o idoso não for do cuidador
     * @throws AcessoNegadoException         se o idoso informar outro idoso
     * @throws SessaoInvalidaException       se o idoso do token não existir mais
     */
    public Conversa resolver(AuthenticatedUser usuario, Long idosoId) {
        if (usuario.isCuidador()) {
            if (idosoId == null) {
                throw new ParametroInvalidoException("idosoId", "Informe o idoso da conversa");
            }
            if (idosoRepository.findByIdAndCuidadorId(idosoId, usuario.cuidadorId()).isEmpty()) {
                throw new RecursoNaoEncontradoException("Idoso não encontrado");
            }
            return new Conversa(idosoId, Remetente.CUIDADOR);
        }
        Long proprio = exigirIdoso(usuario);
        if (idosoId != null && !idosoId.equals(proprio)) {
            throw new AcessoNegadoException();
        }
        return new Conversa(proprio, Remetente.IDOSO);
    }

    /**
     * @return o ID do idoso do token
     * @throws SessaoInvalidaException se o idoso do token não existir mais
     */
    public Long exigirIdoso(AuthenticatedUser usuario) {
        Long idosoId = usuario.idosoId();
        if (idosoRepository.findById(idosoId).isEmpty()) {
            throw new SessaoInvalidaException();
        }
        return idosoId;
    }

    /**
     * @param idosoId idoso da conversa
     * @param lado    lado de quem chama (quem escreve agora, ou quem lê)
     */
    public record Conversa(Long idosoId, Remetente lado) {
    }
}
