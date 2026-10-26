package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.AcessoAConversaService;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Marca como lidas as mensagens que quem chama RECEBEU na conversa (as escritas pelo outro lado). Idempotente: as já
 * lidas não mudam e chamar de novo marca 0.
 */
@Service
public class MarcarMensagensComoLidasUseCase {

    private final AcessoAConversaService acessoAConversa;
    private final MensagemRepositoryPort mensagemRepository;
    private final Clock clock;

    public MarcarMensagensComoLidasUseCase(AcessoAConversaService acessoAConversa,
                                           MensagemRepositoryPort mensagemRepository, Clock clock) {
        this.acessoAConversa = acessoAConversa;
        this.mensagemRepository = mensagemRepository;
        this.clock = clock;
    }

    /**
     * @return quantas mensagens foram marcadas agora
     * @see AcessoAConversaService#resolver(AuthenticatedUser, Long) recusas
     */
    public int executar(AuthenticatedUser usuario, Long pacienteId) {
        AcessoAConversaService.Conversa conversa = acessoAConversa.resolver(usuario, pacienteId);
        return mensagemRepository.marcarComoLidas(conversa.pacienteId(), conversa.lado().outroLado(),
                LocalDateTime.now(clock));
    }
}
