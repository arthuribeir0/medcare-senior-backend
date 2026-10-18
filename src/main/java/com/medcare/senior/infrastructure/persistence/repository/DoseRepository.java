package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import com.medcare.senior.infrastructure.persistence.projection.DoseDetalhadaProjection;
import com.medcare.senior.infrastructure.persistence.projection.DoseVencidaProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Os deletes abaixo recebem o status como parâmetro e são sempre chamados com PENDENTE pelo adapter:
 * doses registradas nunca são apagadas por eles. O único UPDATE (atualizarStatusSeAtual) também é sempre chamado
 * com statusEsperado = PENDENTE.
 */
@Repository
public interface DoseRepository extends JpaRepository<DoseEntity, Long> {

    @Query("SELECT d.horarioProgramado FROM DoseEntity d "
            + "WHERE d.tratamento.id = :tratamentoId AND d.horarioProgramado >= :desde")
    List<LocalDateTime> findHorariosProgramadosDesde(@Param("tratamentoId") Long tratamentoId,
                                                     @Param("desde") LocalDateTime desde);

    // JOINs internos: dose anonimizada (sem tratamento) fica de fora. Da mais antiga para a mais nova, para o lote
    // sempre começar pelas mais atrasadas. Usa o índice (status, horario_programado) da V11.
    @Query("SELECT new com.medcare.senior.infrastructure.persistence.projection.DoseVencidaProjection("
            + "d.id, t.id, d.horarioProgramado, i.cuidador.id, i.nome, m.nome) "
            + "FROM DoseEntity d JOIN d.tratamento t JOIN t.medicamento m JOIN m.idoso i "
            + "WHERE d.status = :status AND d.horarioProgramado <= :vencidasAte "
            + "AND t.removidoEm IS NULL AND m.removidoEm IS NULL "
            + "ORDER BY d.horarioProgramado, d.id")
    List<DoseVencidaProjection> findVencidasByStatus(@Param("status") StatusDose status,
                                                     @Param("vencidasAte") LocalDateTime vencidasAte,
                                                     Pageable pagina);

    // JOINs internos: dose anonimizada (sem tratamento) fica de fora; removidos (tratamento ou medicamento) também.
    // Intervalo semiaberto [inicio, fim): um dia é [data 00:00, data+1 00:00).
    @Query("SELECT new com.medcare.senior.infrastructure.persistence.projection.DoseDetalhadaProjection("
            + "d.id, t.id, d.horarioProgramado, d.horaConfirmacao, d.status, i.id, i.nome, m.id, m.nome, m.dosagem, m.tipo) "
            + "FROM DoseEntity d JOIN d.tratamento t JOIN t.medicamento m JOIN m.idoso i "
            + "WHERE i.id = :idosoId AND d.horarioProgramado >= :inicio AND d.horarioProgramado < :fim "
            + "AND t.removidoEm IS NULL AND m.removidoEm IS NULL "
            + "ORDER BY d.horarioProgramado, d.id")
    List<DoseDetalhadaProjection> findDetalhadasDoIdosoEntre(@Param("idosoId") Long idosoId,
                                                             @Param("inicio") LocalDateTime inicio,
                                                             @Param("fim") LocalDateTime fim);

    // Transição atômica: só muda se a dose ainda estiver no status esperado (PENDENTE). Se uma confirmação
    // concorrente chegou antes, nenhuma linha muda e o retorno é 0.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE DoseEntity d SET d.status = :novoStatus, d.horaConfirmacao = :horaConfirmacao "
            + "WHERE d.id = :id AND d.status = :statusEsperado")
    int atualizarStatusSeAtual(@Param("id") Long id,
                               @Param("statusEsperado") StatusDose statusEsperado,
                               @Param("novoStatus") StatusDose novoStatus,
                               @Param("horaConfirmacao") LocalDateTime horaConfirmacao);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DoseEntity d WHERE d.tratamento.id = :tratamentoId "
            + "AND d.status = :status AND d.horarioProgramado > :agora")
    int deleteFuturasByTratamentoIdAndStatus(@Param("tratamentoId") Long tratamentoId,
                                             @Param("status") StatusDose status,
                                             @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DoseEntity d WHERE d.status = :status AND d.horarioProgramado > :agora "
            + "AND d.tratamento.id IN (SELECT t.id FROM TratamentoEntity t WHERE t.medicamento.id = :medicamentoId)")
    int deleteFuturasByMedicamentoIdAndStatus(@Param("medicamentoId") Long medicamentoId,
                                              @Param("status") StatusDose status,
                                              @Param("agora") LocalDateTime agora);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM DoseEntity d WHERE d.status = :status AND d.tratamento.id IN ("
            + "SELECT t.id FROM TratamentoEntity t WHERE t.medicamento.idoso.cuidador.id = :cuidadorId)")
    int deleteByCuidadorIdAndStatus(@Param("cuidadorId") Long cuidadorId, @Param("status") StatusDose status);
}
