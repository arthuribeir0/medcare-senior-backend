package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.StatusEventoNotificacao;
import com.medcare.senior.infrastructure.persistence.entity.EventoNotificacaoEntity;
import com.medcare.senior.infrastructure.persistence.projection.EventoParaRetentativaView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Toda mudança de estado é um UPDATE condicionado ao estado esperado (status e, na reivindicação, tentativas): se
 * outro job mudou o evento antes, nenhuma linha muda e o retorno é 0.
 */
@Repository
public interface EventoNotificacaoRepository extends JpaRepository<EventoNotificacaoEntity, Long> {

    // INSERT idempotente pelo UNIQUE de dose_id. RETURNING devolve o id só quando a linha foi criada (lista vazia se
    // a dose já tinha evento). Sem @Modifying: o comando devolve um resultado, como uma consulta.
    @Query(value = "INSERT INTO evento_notificacao (status, tentativas, dose_id) VALUES ('PENDENTE', 0, :doseId) "
            + "ON CONFLICT (dose_id) DO NOTHING RETURNING id", nativeQuery = true)
    List<Long> inserirSeAusente(@Param("doseId") Long doseId);

    // Retentativa (RN-010). O intervalo crescente sai de tentativas e ultima_tentativa, sem coluna extra:
    // ultima_tentativa + base * 2^(tentativas - 1) <= agora. Nunca tentado (ultima_tentativa nula) e esgotado
    // (tentativas >= max) entram direto. LEFT JOINs: a dose pode ter ficado sem dono (anonimizada ou de item
    // removido) e o evento precisa ser encerrado mesmo assim. Usa o índice parcial idx_evento_notificacao_pendente.
    @Query(value = "SELECT e.id AS eventoId, e.tentativas AS tentativas, d.id AS doseId, d.status AS statusDose, "
            + "d.horario_programado AS horarioProgramado, "
            + "(i.id IS NULL OR t.removido_em IS NOT NULL OR m.removido_em IS NOT NULL) AS semDono, "
            + "i.cuidador_id AS cuidadorId, i.id AS pacienteId, i.nome AS nomePaciente "
            + "FROM evento_notificacao e JOIN dose d ON d.id = e.dose_id "
            + "LEFT JOIN tratamento t ON t.id = d.tratamento_id LEFT JOIN medicamento m ON m.id = t.medicamento_id "
            + "LEFT JOIN paciente i ON i.id = m.paciente_id "
            + "WHERE e.status = 'PENDENTE' AND (e.tentativas >= :maxTentativas OR e.ultima_tentativa IS NULL "
            + "OR e.ultima_tentativa + make_interval(mins => :intervaloBaseMinutos * CAST(power(2, e.tentativas - 1) AS integer)) "
            + "<= :agora) "
            + "ORDER BY e.ultima_tentativa NULLS FIRST, e.id LIMIT :limite", nativeQuery = true)
    List<EventoParaRetentativaView> findParaRetentativa(@Param("agora") LocalDateTime agora,
                                                        @Param("maxTentativas") int maxTentativas,
                                                        @Param("intervaloBaseMinutos") int intervaloBaseMinutos,
                                                        @Param("limite") int limite);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EventoNotificacaoEntity e SET e.tentativas = e.tentativas + 1, e.ultimaTentativa = :agora "
            + "WHERE e.id = :id AND e.status = :pendente AND e.tentativas = :tentativasEsperadas")
    int reivindicar(@Param("id") Long id,
                    @Param("tentativasEsperadas") int tentativasEsperadas,
                    @Param("agora") LocalDateTime agora,
                    @Param("pendente") StatusEventoNotificacao pendente);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE EventoNotificacaoEntity e SET e.status = :novoStatus, e.motivoFalha = :motivo "
            + "WHERE e.id = :id AND e.status = :pendente")
    int finalizar(@Param("id") Long id,
                  @Param("novoStatus") StatusEventoNotificacao novoStatus,
                  @Param("motivo") MotivoFalhaNotificacao motivo,
                  @Param("pendente") StatusEventoNotificacao pendente);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM EventoNotificacaoEntity e WHERE e.doseId IN (SELECT d.id FROM DoseEntity d "
            + "WHERE d.tratamento.medicamento.paciente.cuidador.id = :cuidadorId)")
    int deleteByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
