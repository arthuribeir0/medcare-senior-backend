package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.AcessoAConversaService;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.mensagem.Mensagem;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Envia uma mensagem na conversa de um idoso com o seu cuidador (UC-010). Mensagem vazia é recusada (fluxo 3a).
 */
@Service
public class EnviarMensagemUseCase {

    private final AcessoAConversaService acessoAConversa;
    private final MensagemRepositoryPort mensagemRepository;
    private final Clock clock;

    public EnviarMensagemUseCase(AcessoAConversaService acessoAConversa, MensagemRepositoryPort mensagemRepository,
                                 Clock clock) {
        this.acessoAConversa = acessoAConversa;
        this.mensagemRepository = mensagemRepository;
        this.clock = clock;
    }

    /**
     * @param idosoId idoso da conversa (obrigatório para o cuidador; o idoso escreve sempre na própria conversa)
     * @throws com.medcare.senior.domain.exception.ParametroInvalidoException se o conteúdo for vazio ou maior que 500
     *                                                                        caracteres, ou faltar o idoso
     * @see AcessoAConversaService#resolver(AuthenticatedUser, Long) demais recusas
     */
    public Mensagem executar(AuthenticatedUser usuario, Long idosoId, String conteudo) {
        AcessoAConversaService.Conversa conversa = acessoAConversa.resolver(usuario, idosoId);
        return mensagemRepository.salvar(Mensagem.nova(conversa.idosoId(), conversa.lado(), conteudo,
                LocalDateTime.now(clock)));
    }
}
