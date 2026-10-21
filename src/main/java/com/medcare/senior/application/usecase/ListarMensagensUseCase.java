package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.AcessoAConversaService;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.historico.Pagina;
import com.medcare.senior.domain.historico.Paginacao;
import com.medcare.senior.domain.mensagem.Mensagem;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Mensagens de uma conversa (UC-010, passo 41), paginadas, da mais recente para a mais antiga.
 */
@Service
@RequiredArgsConstructor
public class ListarMensagensUseCase {

    private final AcessoAConversaService acessoAConversa;
    private final MensagemRepositoryPort mensagemRepository;

    /**
     * @throws com.medcare.senior.domain.exception.ParametroInvalidoException se a página for negativa, o tamanho fora
     *                                                                        de 1 a 100 ou faltar o idoso
     * @see AcessoAConversaService#resolver(AuthenticatedUser, Long) demais recusas
     */
    public Pagina<Mensagem> executar(AuthenticatedUser usuario, Long idosoId, Integer pagina, Integer tamanho) {
        Paginacao paginacao = Paginacao.de(pagina, tamanho);
        AcessoAConversaService.Conversa conversa = acessoAConversa.resolver(usuario, idosoId);
        return mensagemRepository.findConversa(conversa.idosoId(), paginacao.pagina(), paginacao.tamanho());
    }
}
