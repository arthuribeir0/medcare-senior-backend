package com.medcare.senior.domain.desconexao;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PedidoDesconexaoRepositoryPort {

    /**
     * Grava um pedido PENDENTE novo.
     *
     * @return vazio se o paciente já tiver um PENDENTE (índice único parcial; ex.: dois pedidos simultâneos)
     */
    Optional<PedidoDesconexao> inserirPendente(PedidoDesconexao pedido);

    /** O PENDENTE gravado do paciente, mesmo que já tenha passado do prazo. */
    Optional<PedidoDesconexao> findPendente(Long pacienteId);

    /** O pedido mais recente do paciente, de qualquer status. */
    Optional<PedidoDesconexao> findMaisRecente(Long pacienteId);

    Optional<PedidoDesconexao> findByIdAndPacienteId(Long id, Long pacienteId);

    /** PENDENTES ainda dentro do prazo dos pacientes informados, dos mais antigos para os mais novos. */
    List<PedidoDesconexao> findPendentesValidos(Collection<Long> pacienteIds, LocalDateTime agora);

    /**
     * Decide um pedido em UM comando: só muda se ainda estiver PENDENTE e dentro do prazo. Duas decisões ao mesmo
     * tempo: só uma muda o pedido.
     *
     * @param novoStatus AUTORIZADO ou RECUSADO
     * @return true se este comando decidiu o pedido
     */
    boolean decidir(Long id, StatusPedidoDesconexao novoStatus, Long cuidadorId, LocalDateTime agora);

    /**
     * Autoriza o PENDENTE do paciente, se houver (desconexão feita pelo cuidador sem esperar a decisão).
     *
     * @return true se havia um pendente válido
     */
    boolean autorizarPendente(Long pacienteId, Long cuidadorId, LocalDateTime agora);

    /** Cancela o PENDENTE do paciente, se houver. @return true se havia um */
    boolean cancelarPendente(Long pacienteId, LocalDateTime agora);

    /** Marca como EXPIRADO os PENDENTES do paciente que passaram do prazo. */
    int expirarVencidos(Long pacienteId, LocalDateTime agora);

    /** Marca como EXPIRADO todos os PENDENTES que passaram do prazo (job). */
    int expirarTodosVencidos(LocalDateTime agora);
}
