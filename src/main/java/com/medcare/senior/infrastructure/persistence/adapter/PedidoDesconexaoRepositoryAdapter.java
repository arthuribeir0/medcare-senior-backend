package com.medcare.senior.infrastructure.persistence.adapter;

import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import com.medcare.senior.infrastructure.persistence.entity.PedidoDesconexaoEntity;
import com.medcare.senior.infrastructure.persistence.repository.PedidoDesconexaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PedidoDesconexaoRepositoryAdapter implements PedidoDesconexaoRepositoryPort {

    // ON CONFLICT no índice único parcial: outro PENDENTE do mesmo paciente faz o INSERT não gravar nada, sem erro (um
    // erro do PostgreSQL abortaria a transação de quem chamou)
    private static final String INSERIR_PENDENTE = "INSERT INTO pedido_desconexao (paciente_id, status, criado_em, expira_em) "
            + "VALUES (?, 'PENDENTE', ?, ?) ON CONFLICT (paciente_id) WHERE status = 'PENDENTE' DO NOTHING RETURNING id";

    private final PedidoDesconexaoRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public Optional<PedidoDesconexao> inserirPendente(PedidoDesconexao pedido) {
        List<Long> ids = jdbcTemplate.queryForList(INSERIR_PENDENTE, Long.class, pedido.pacienteId(),
                Timestamp.valueOf(pedido.criadoEm()), Timestamp.valueOf(pedido.expiraEm()));
        return ids.stream().findFirst().map(id -> new PedidoDesconexao(id, pedido.pacienteId(),
                StatusPedidoDesconexao.PENDENTE, pedido.criadoEm(), pedido.expiraEm(), null, null));
    }

    @Override
    public Optional<PedidoDesconexao> findPendente(Long pacienteId) {
        return repository.findFirstByPacienteIdAndStatus(pacienteId, StatusPedidoDesconexao.PENDENTE).map(this::toDomain);
    }

    @Override
    public Optional<PedidoDesconexao> findMaisRecente(Long pacienteId) {
        return repository.findFirstByPacienteIdOrderByIdDesc(pacienteId).map(this::toDomain);
    }

    @Override
    public Optional<PedidoDesconexao> findByIdAndPacienteId(Long id, Long pacienteId) {
        return repository.findByIdAndPacienteId(id, pacienteId).map(this::toDomain);
    }

    @Override
    public List<PedidoDesconexao> findPendentesValidos(Collection<Long> pacienteIds, LocalDateTime agora) {
        if (pacienteIds.isEmpty()) {
            return List.of();
        }
        return repository.findPendentesValidos(pacienteIds, agora).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean decidir(Long id, StatusPedidoDesconexao novoStatus, Long cuidadorId, LocalDateTime agora) {
        return repository.decidir(id, novoStatus, cuidadorId, agora) == 1;
    }

    @Override
    @Transactional
    public boolean autorizarPendente(Long pacienteId, Long cuidadorId, LocalDateTime agora) {
        return repository.decidirPendenteDoPaciente(pacienteId, StatusPedidoDesconexao.AUTORIZADO, cuidadorId, agora) > 0;
    }

    @Override
    @Transactional
    public boolean cancelarPendente(Long pacienteId, LocalDateTime agora) {
        return repository.decidirPendenteDoPaciente(pacienteId, StatusPedidoDesconexao.CANCELADO, null, agora) > 0;
    }

    @Override
    @Transactional
    public int expirarVencidos(Long pacienteId, LocalDateTime agora) {
        return repository.expirarVencidos(pacienteId, agora);
    }

    @Override
    @Transactional
    public int expirarTodosVencidos(LocalDateTime agora) {
        return repository.expirarTodosVencidos(agora);
    }

    @Override
    public void deleteAllByPacienteId(Long pacienteId) {
        repository.deleteAllByPacienteId(pacienteId);
    }

    private PedidoDesconexao toDomain(PedidoDesconexaoEntity e) {
        return new PedidoDesconexao(e.getId(), e.getPacienteId(), e.getStatus(), e.getCriadoEm(), e.getExpiraEm(),
                e.getDecididoEm(), e.getDecididoPorCuidadorId());
    }

}
