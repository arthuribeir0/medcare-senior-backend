package com.medcare.senior.application.service;

import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * De quem é a conversa (UC-010) e de que lado está quem chama. A conversa é sempre de um paciente com o seu cuidador.
 *
 * <ul>
 *   <li>Cuidador: informa o paciente, que precisa ser dele (paciente alheio responde 404, como inexistente). A conta
 *       excluída já é barrada antes, com 401 SESSAO_INVALIDA, pelo SessaoDoCuidadorInterceptor.</li>
 *   <li>Paciente: a conversa é a dele (o paciente vem do token). Informar outro paciente é 403 ACESSO_NEGADO; paciente que não
 *       existe mais é 401 SESSAO_INVALIDA.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AcessoAConversaService {

    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param pacienteId paciente informado na requisição (obrigatório para o cuidador; opcional para o paciente)
     * @throws ParametroInvalidoException    se o cuidador não informar o paciente
     * @throws RecursoNaoEncontradoException se o paciente não for do cuidador
     * @throws AcessoNegadoException         se o paciente informar outro paciente
     * @throws SessaoInvalidaException       se o paciente do token não existir mais
     */
    public Conversa resolver(AuthenticatedUser usuario, Long pacienteId) {
        if (usuario.isCuidador()) {
            if (pacienteId == null) {
                throw new ParametroInvalidoException("pacienteId", "Informe o paciente da conversa");
            }
            if (pacienteRepository.findByIdAndCuidadorId(pacienteId, usuario.cuidadorId()).isEmpty()) {
                throw new RecursoNaoEncontradoException("Paciente não encontrado");
            }
            return new Conversa(pacienteId, Remetente.CUIDADOR);
        }
        Long proprio = exigirPaciente(usuario);
        if (pacienteId != null && !pacienteId.equals(proprio)) {
            throw new AcessoNegadoException();
        }
        return new Conversa(proprio, Remetente.PACIENTE);
    }

    /**
     * @return o ID do paciente do token
     * @throws SessaoInvalidaException se o paciente do token não existir mais
     */
    public Long exigirPaciente(AuthenticatedUser usuario) {
        Long pacienteId = usuario.pacienteId();
        if (pacienteRepository.findById(pacienteId).isEmpty()) {
            throw new SessaoInvalidaException();
        }
        return pacienteId;
    }

    /**
     * @param pacienteId paciente da conversa
     * @param lado    lado de quem chama (quem escreve agora, ou quem lê)
     */
    public record Conversa(Long pacienteId, Remetente lado) {
    }
}
