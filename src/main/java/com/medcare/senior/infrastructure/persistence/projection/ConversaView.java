package com.medcare.senior.infrastructure.persistence.projection;

import java.time.LocalDateTime;

/**
 * Linha da lista de conversas (consulta nativa): o idoso, a última mensagem (colunas nulas sem mensagens) e as não
 * lidas pelo leitor.
 */
public interface ConversaView {

    Long getIdosoId();

    String getIdosoNome();

    String getConteudo();

    String getRemetente();

    LocalDateTime getCriadaEm();

    Long getNaoLidas();
}
