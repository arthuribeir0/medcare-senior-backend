package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.DesconexaoDoAparelhoService;
import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import com.medcare.senior.domain.exception.PedidoDesconexaoEncerradoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * O cuidador vinculado autoriza ou recusa o pedido do paciente para sair da conta.
 *
 * <ul>
 *   <li>Paciente de outro cuidador, ou pedido de outro paciente: 404, como nas demais rotas (não revela IDs).</li>
 *   <li>A decisão é um UPDATE condicional (só se ainda PENDENTE e no prazo): com duas decisões ao mesmo tempo, só
 *       uma vale.</li>
 *   <li>Idempotente: repetir a mesma decisão devolve o pedido como está, sem repetir o efeito. Decidir o contrário,
 *       ou um pedido cancelado ou vencido, responde 409 PEDIDO_ENCERRADO.</li>
 *   <li>Autorizar desconecta o aparelho do paciente na mesma transação ({@link DesconexaoDoAparelhoService}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DecidirPedidoDesconexaoUseCase {

    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final DesconexaoDoAparelhoService desconexaoDoAparelho;
    private final Clock clock;

    @Transactional
    public PedidoDesconexao autorizar(Long pacienteId, Long pedidoId, Long cuidadorId) {
        return decidir(pacienteId, pedidoId, cuidadorId, StatusPedidoDesconexao.AUTORIZADO);
    }

    @Transactional
    public PedidoDesconexao recusar(Long pacienteId, Long pedidoId, Long cuidadorId) {
        return decidir(pacienteId, pedidoId, cuidadorId, StatusPedidoDesconexao.RECUSADO);
    }

    private PedidoDesconexao decidir(Long pacienteId, Long pedidoId, Long cuidadorId, StatusPedidoDesconexao decisao) {
        pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));
        // Dono conferido ANTES do UPDATE (que filtra só pelo ID do pedido): pedido de outro paciente nunca é decidido
        buscar(pedidoId, pacienteId);
        LocalDateTime agora = LocalDateTime.now(clock);
        if (pedidoRepository.decidir(pedidoId, decisao, cuidadorId, agora)) {
            if (decisao == StatusPedidoDesconexao.AUTORIZADO) {
                desconexaoDoAparelho.desconectar(pacienteId);
            }
            return buscar(pedidoId, pacienteId);
        }
        PedidoDesconexao atual = buscar(pedidoId, pacienteId).vistoEm(agora);
        if (atual.status() == decisao) {
            return atual;
        }
        throw new PedidoDesconexaoEncerradoException();
    }

    private PedidoDesconexao buscar(Long pedidoId, Long pacienteId) {
        return pedidoRepository.findByIdAndPacienteId(pedidoId, pacienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido não encontrado"));
    }
}
