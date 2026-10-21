package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.AcessoAConversaService;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import com.medcare.senior.domain.mensagem.ResumoConversa;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Lista de conversas: o cuidador vê uma por idoso seu (inclusive sem mensagens); o idoso vê a sua única conversa.
 * Cada uma traz a última mensagem e quantas mensagens do outro lado quem chama ainda não leu.
 */
@Service
@RequiredArgsConstructor
public class ListarConversasUseCase {

    private final AcessoAConversaService acessoAConversa;
    private final MensagemRepositoryPort mensagemRepository;

    /**
     * @throws com.medcare.senior.domain.exception.SessaoInvalidaException se o idoso do token não existir mais
     */
    public List<ResumoConversa> executar(AuthenticatedUser usuario) {
        if (usuario.isCuidador()) {
            return mensagemRepository.findConversasDoCuidador(usuario.cuidadorId());
        }
        return List.of(mensagemRepository.findConversaDoIdoso(acessoAConversa.exigirIdoso(usuario)));
    }
}
