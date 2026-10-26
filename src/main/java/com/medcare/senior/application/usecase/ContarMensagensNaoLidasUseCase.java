package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.AcessoAConversaService;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.mensagem.MensagemRepositoryPort;
import com.medcare.senior.domain.mensagem.Remetente;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Total de mensagens que quem chama recebeu e ainda não leu: para o cuidador, somando as conversas de todos os seus
 * pacientes; para o paciente, as do cuidador.
 */
@Service
@RequiredArgsConstructor
public class ContarMensagensNaoLidasUseCase {

    private final AcessoAConversaService acessoAConversa;
    private final MensagemRepositoryPort mensagemRepository;

    /**
     * @throws com.medcare.senior.domain.exception.SessaoInvalidaException se o paciente do token não existir mais
     */
    public long executar(AuthenticatedUser usuario) {
        if (usuario.isCuidador()) {
            return mensagemRepository.contarNaoLidasDoCuidador(usuario.cuidadorId());
        }
        return mensagemRepository.contarNaoLidas(acessoAConversa.exigirPaciente(usuario), Remetente.CUIDADOR);
    }
}
