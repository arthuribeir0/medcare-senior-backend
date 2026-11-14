/*
 * Interface Spring Data JPA de PedidoDesconexaoEntity: consultas e atualizações no banco. Métodos:
 * findFirstByPacienteIdAndStatus, findFirstByPacienteIdOrderByIdDesc, findByIdAndPacienteId, findPendentesValidos,
 * decidir, decidirPendenteDoPaciente, expirarVencidos, expirarTodosVencidos, deleteAllByPacienteId.
 */
package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import com.medcare.senior.infrastructure.persistence.entity.PedidoDesconexaoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoDesconexaoRepository extends JpaRepository<PedidoDesconexaoEntity, Long> {

    String PENDENTE = "com.medcare.senior.domain.desconexao.StatusPedidoDesconexao.PENDENTE";

    Optional<PedidoDesconexaoEntity> findFirstByPacienteIdAndStatus(Long pacienteId, StatusPedidoDesconexao status);

    Optional<PedidoDesconexaoEntity> findFirstByPacienteIdOrderByIdDesc(Long pacienteId);

    Optional<PedidoDesconexaoEntity> findByIdAndPacienteId(Long id, Long pacienteId);

    @Query("SELECT p FROM PedidoDesconexaoEntity p WHERE p.pacienteId IN :pacienteIds AND p.status = " + PENDENTE
            + " AND p.expiraEm > :agora ORDER BY p.criadoEm, p.id")
    List<PedidoDesconexaoEntity> findPendentesValidos(@Param("pacienteIds") Collection<Long> pacienteIds,
                                                      @Param("agora") LocalDateTime agora);

    // Decisão em UM comando condicional: com duas decisões ao mesmo tempo, a segunda espera a trava da linha,
    // reavalia o WHERE e não muda nada
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PedidoDesconexaoEntity p SET p.status = :novoStatus, p.decididoEm = :agora, "
            + "p.decididoPorCuidadorId = :cuidadorId WHERE p.id = :id AND p.status = " + PENDENTE
            + " AND p.expiraEm > :agora")
    int decidir(@Param("id") Long id, @Param("novoStatus") StatusPedidoDesconexao novoStatus,
                @Param("cuidadorId") Long cuidadorId, @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PedidoDesconexaoEntity p SET p.status = :novoStatus, p.decididoEm = :agora, "
            + "p.decididoPorCuidadorId = :cuidadorId WHERE p.pacienteId = :pacienteId AND p.status = " + PENDENTE
            + " AND p.expiraEm > :agora")
    int decidirPendenteDoPaciente(@Param("pacienteId") Long pacienteId,
                                  @Param("novoStatus") StatusPedidoDesconexao novoStatus,
                                  @Param("cuidadorId") Long cuidadorId, @Param("agora") LocalDateTime agora);

    // decidido_em = o próprio prazo (quando o pedido de fato venceu), não a hora em que o job passou
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PedidoDesconexaoEntity p SET p.status = "
            + "com.medcare.senior.domain.desconexao.StatusPedidoDesconexao.EXPIRADO, p.decididoEm = p.expiraEm "
            + "WHERE p.pacienteId = :pacienteId AND p.status = " + PENDENTE + " AND p.expiraEm <= :agora")
    int expirarVencidos(@Param("pacienteId") Long pacienteId, @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE PedidoDesconexaoEntity p SET p.status = "
            + "com.medcare.senior.domain.desconexao.StatusPedidoDesconexao.EXPIRADO, p.decididoEm = p.expiraEm "
            + "WHERE p.status = " + PENDENTE + " AND p.expiraEm <= :agora")
    int expirarTodosVencidos(@Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM PedidoDesconexaoEntity p WHERE p.pacienteId = :pacienteId")
    int deleteAllByPacienteId(@Param("pacienteId") Long pacienteId);
}
