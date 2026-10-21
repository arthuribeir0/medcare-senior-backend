package com.medcare.senior.infrastructure.persistence.repository;

import com.medcare.senior.domain.mensagem.Remetente;
import com.medcare.senior.infrastructure.persistence.entity.MensagemEntity;
import com.medcare.senior.infrastructure.persistence.projection.ConversaView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MensagemRepository extends JpaRepository<MensagemEntity, Long> {

    // Conversa do idoso, da mais recente para a mais antiga; usa o índice (idoso_id, criada_em) da V14
    @Query(value = "SELECT m FROM MensagemEntity m WHERE m.idosoId = :idosoId ORDER BY m.criadaEm DESC, m.id DESC",
            countQuery = "SELECT COUNT(m) FROM MensagemEntity m WHERE m.idosoId = :idosoId")
    Page<MensagemEntity> findConversa(@Param("idosoId") Long idosoId, Pageable pagina);

    // Só as ainda não lidas: chamar de novo não muda nada (idempotente) e o retorno conta só as marcadas agora
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MensagemEntity m SET m.lidaEm = :agora "
            + "WHERE m.idosoId = :idosoId AND m.remetente = :remetente AND m.lidaEm IS NULL")
    int marcarComoLidas(@Param("idosoId") Long idosoId, @Param("remetente") Remetente remetente,
                        @Param("agora") LocalDateTime agora);

    // Índice parcial (idoso_id, remetente) WHERE lida_em IS NULL da V14
    long countByIdosoIdAndRemetenteAndLidaEmIsNull(Long idosoId, Remetente remetente);

    @Query("SELECT COUNT(m) FROM MensagemEntity m, IdosoEntity i "
            + "WHERE i.id = m.idosoId AND i.cuidador.id = :cuidadorId AND m.remetente = :remetente AND m.lidaEm IS NULL")
    long countNaoLidasDoCuidador(@Param("cuidadorId") Long cuidadorId, @Param("remetente") Remetente remetente);

    // Lista de conversas: um idoso por linha, com a última mensagem (LATERAL ... LIMIT 1 pelo índice da conversa) e as
    // não lidas escritas por :remetente (índice parcial). Idoso sem mensagens vem com as colunas da mensagem nulas.
    String CONVERSAS = "SELECT i.id AS idosoId, i.nome AS idosoNome, u.conteudo AS conteudo, u.remetente AS remetente, "
            + "u.criada_em AS criadaEm, (SELECT COUNT(*) FROM mensagem n WHERE n.idoso_id = i.id "
            + "AND n.remetente = :remetente AND n.lida_em IS NULL) AS naoLidas "
            + "FROM idoso i LEFT JOIN LATERAL (SELECT m.conteudo, m.remetente, m.criada_em FROM mensagem m "
            + "WHERE m.idoso_id = i.id ORDER BY m.criada_em DESC, m.id DESC LIMIT 1) u ON TRUE ";

    @Query(value = CONVERSAS + "WHERE i.cuidador_id = :cuidadorId ORDER BY u.criada_em DESC NULLS LAST, lower(i.nome), i.id",
            nativeQuery = true)
    List<ConversaView> findConversasDoCuidador(@Param("cuidadorId") Long cuidadorId, @Param("remetente") String remetente);

    @Query(value = CONVERSAS + "WHERE i.id = :idosoId", nativeQuery = true)
    Optional<ConversaView> findConversaDoIdoso(@Param("idosoId") Long idosoId, @Param("remetente") String remetente);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MensagemEntity m WHERE m.idosoId IN "
            + "(SELECT i.id FROM IdosoEntity i WHERE i.cuidador.id = :cuidadorId)")
    int deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
