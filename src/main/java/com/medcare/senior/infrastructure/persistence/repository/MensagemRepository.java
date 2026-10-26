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

    // Conversa do paciente, da mais recente para a mais antiga; usa o índice (paciente_id, criada_em) da V14
    @Query(value = "SELECT m FROM MensagemEntity m WHERE m.pacienteId = :pacienteId ORDER BY m.criadaEm DESC, m.id DESC",
            countQuery = "SELECT COUNT(m) FROM MensagemEntity m WHERE m.pacienteId = :pacienteId")
    Page<MensagemEntity> findConversa(@Param("pacienteId") Long pacienteId, Pageable pagina);

    // Só as ainda não lidas: chamar de novo não muda nada (idempotente) e o retorno conta só as marcadas agora
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE MensagemEntity m SET m.lidaEm = :agora "
            + "WHERE m.pacienteId = :pacienteId AND m.remetente = :remetente AND m.lidaEm IS NULL")
    int marcarComoLidas(@Param("pacienteId") Long pacienteId, @Param("remetente") Remetente remetente,
                        @Param("agora") LocalDateTime agora);

    // Índice parcial (paciente_id, remetente) WHERE lida_em IS NULL da V14
    long countByPacienteIdAndRemetenteAndLidaEmIsNull(Long pacienteId, Remetente remetente);

    @Query("SELECT COUNT(m) FROM MensagemEntity m, PacienteEntity i "
            + "WHERE i.id = m.pacienteId AND i.cuidador.id = :cuidadorId AND m.remetente = :remetente AND m.lidaEm IS NULL")
    long countNaoLidasDoCuidador(@Param("cuidadorId") Long cuidadorId, @Param("remetente") Remetente remetente);

    // Lista de conversas: um paciente por linha, com a última mensagem (LATERAL ... LIMIT 1 pelo índice da conversa) e as
    // não lidas escritas por :remetente (índice parcial). Paciente sem mensagens vem com as colunas da mensagem nulas.
    String CONVERSAS = "SELECT i.id AS pacienteId, i.nome AS pacienteNome, u.conteudo AS conteudo, u.remetente AS remetente, "
            + "u.criada_em AS criadaEm, (SELECT COUNT(*) FROM mensagem n WHERE n.paciente_id = i.id "
            + "AND n.remetente = :remetente AND n.lida_em IS NULL) AS naoLidas "
            + "FROM paciente i LEFT JOIN LATERAL (SELECT m.conteudo, m.remetente, m.criada_em FROM mensagem m "
            + "WHERE m.paciente_id = i.id ORDER BY m.criada_em DESC, m.id DESC LIMIT 1) u ON TRUE ";

    @Query(value = CONVERSAS + "WHERE i.cuidador_id = :cuidadorId ORDER BY u.criada_em DESC NULLS LAST, lower(i.nome), i.id",
            nativeQuery = true)
    List<ConversaView> findConversasDoCuidador(@Param("cuidadorId") Long cuidadorId, @Param("remetente") String remetente);

    @Query(value = CONVERSAS + "WHERE i.id = :pacienteId", nativeQuery = true)
    Optional<ConversaView> findConversaDoPaciente(@Param("pacienteId") Long pacienteId, @Param("remetente") String remetente);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM MensagemEntity m WHERE m.pacienteId IN "
            + "(SELECT i.id FROM PacienteEntity i WHERE i.cuidador.id = :cuidadorId)")
    int deleteAllByCuidadorId(@Param("cuidadorId") Long cuidadorId);
}
