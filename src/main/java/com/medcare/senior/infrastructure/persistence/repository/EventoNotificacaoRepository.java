package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.notificacao.MotivoFalhaNotificacao;
import com.medcare.senior.domain.notificacao.StatusEventoNotificacao;
import com.medcare.senior.infrastructure.persistence.entity.EventoNotificacaoEntity;
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
            + "WHERE d.tratamento.medicamento.idoso.cuidador.id = :cuidadorId)")
    int deleteByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
