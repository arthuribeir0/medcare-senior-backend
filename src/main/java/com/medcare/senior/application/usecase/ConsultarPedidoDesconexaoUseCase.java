package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * O pedido de desconexão mais recente do paciente autenticado, com o status valendo agora (um pendente vencido já sai
 * como EXPIRADO, mesmo antes do job). Usado pela tela "Aguardando autorização" do app.
 */
@Service
@RequiredArgsConstructor
public class ConsultarPedidoDesconexaoUseCase {

    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final Clock clock;

    /**
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se o paciente nunca pediu para sair
     */
    public PedidoDesconexao executar(Long pacienteId) {
        LocalDateTime agora = LocalDateTime.now(clock);
        return pedidoRepository.findMaisRecente(pacienteId)
                .map(pedido -> pedido.vistoEm(agora))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Nenhum pedido para sair da conta"));
    }
}
