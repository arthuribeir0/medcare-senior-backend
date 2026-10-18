package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.infrastructure.persistence.entity.DoseEntity;
import com.medcare.senior.infrastructure.persistence.projection.DoseDetalhadaProjection;
import com.medcare.senior.infrastructure.persistence.projection.DoseParaAvisoView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // Fase de AVISO da varredura. Nativa por causa da soma horário + tolerância em minutos (make_interval), que é
    // por linha (a tolerância por cuidador entra no lugar do parâmetro). "horario > :encerradasAte" limita a faixa
    // no índice (status, horario_programado) da V11; o NOT EXISTS usa o UNIQUE de evento_notificacao.dose_id.
    // JOINs internos: dose anonimizada (sem tratamento) fica de fora. Da mais antiga para a mais nova.
    @Query(value = "SELECT d.id AS doseId, d.horario_programado AS horarioProgramado, i.cuidador_id AS cuidadorId, "
            + "i.id AS idosoId, i.nome AS nomeIdoso "
            + "FROM dose d JOIN tratamento t ON t.id = d.tratamento_id JOIN medicamento m ON m.id = t.medicamento_id "
            + "JOIN idoso i ON i.id = m.idoso_id "
            + "WHERE d.status = 'PENDENTE' AND t.removido_em IS NULL AND m.removido_em IS NULL "
            + "AND d.horario_programado > :encerradasAte "
            + "AND d.horario_programado + make_interval(mins => :toleranciaMinutos) <= :agora "
            + "AND NOT EXISTS (SELECT 1 FROM evento_notificacao e WHERE e.dose_id = d.id) "
            + "ORDER BY d.horario_programado, d.id LIMIT :limite", nativeQuery = true)
    List<DoseParaAvisoView> findParaAviso(@Param("agora") LocalDateTime agora,
                                          @Param("toleranciaMinutos") int toleranciaMinutos,
                                          @Param("encerradasAte") LocalDateTime encerradasAte,
                                          @Param("limite") int limite);

    // Fase de ENCERRAMENTO da varredura: só a dose (o encerramento não avisa ninguém). Mesmos filtros de dono e
    // remoção; usa o índice (status, horario_programado) da V11.
    @Query("SELECT d FROM DoseEntity d JOIN d.tratamento t JOIN t.medicamento m "
            + "WHERE d.status = :status AND d.horarioProgramado <= :encerradasAte "
            + "AND t.removidoEm IS NULL AND m.removidoEm IS NULL "
            + "ORDER BY d.horarioProgramado, d.id")
    List<DoseEntity> findEncerradasByStatus(@Param("status") StatusDose status,
                                         @Param("encerradasAte") LocalDateTime encerradasAte,
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

    @Query("SELECT new com.medcare.senior.infrastructure.persistence.projection.DoseDetalhadaProjection("
            + "d.id, t.id, d.horarioProgramado, d.horaConfirmacao, d.status, i.id, i.nome, m.id, m.nome, m.dosagem, m.tipo) "
            + "FROM DoseEntity d JOIN d.tratamento t JOIN t.medicamento m JOIN m.idoso i "
            + "WHERE d.id = :doseId AND i.id = :idosoId AND t.removidoEm IS NULL AND m.removidoEm IS NULL")
    Optional<DoseDetalhadaProjection> findDetalhadaByIdAndIdosoId(@Param("doseId") Long doseId,
                                                                  @Param("idosoId") Long idosoId);

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
