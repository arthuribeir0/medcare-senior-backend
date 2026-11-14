package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pedidos de desconexão aguardando o cuidador autenticado (só dos pacientes dele e ainda dentro do prazo), dos mais
 * antigos para os mais novos. Duas consultas, qualquer que seja o número de pacientes.
 */
@Service
@RequiredArgsConstructor
public class ListarPedidosDesconexaoPendentesUseCase {

    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final Clock clock;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public List<PedidoPendente> executar(Long cuidadorId) {
        Map<Long, Paciente> pacientes = pacienteRepository.findAllByCuidadorId(cuidadorId).stream()
                .collect(Collectors.toMap(Paciente::getId, Function.identity()));
        return pedidoRepository.findPendentesValidos(pacientes.keySet(), LocalDateTime.now(clock)).stream()
                .map(pedido -> new PedidoPendente(pedido, pacientes.get(pedido.pacienteId()).getNome()))
                .toList();
    }

    public record PedidoPendente(PedidoDesconexao pedido, String pacienteNome) {
    }
}
