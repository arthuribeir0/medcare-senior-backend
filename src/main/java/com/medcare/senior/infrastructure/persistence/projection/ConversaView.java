package com.medcare.senior.infrastructure.persistence.projection;

import java.time.LocalDateTime;

/**
 * Linha da lista de conversas (consulta nativa): o paciente, a última mensagem (colunas nulas sem mensagens) e as não
 * lidas pelo leitor.
 */
public interface ConversaView {

    Long getPacienteId();

    String getPacienteNome();

    String getConteudo();

    String getRemetente();

    LocalDateTime getCriadaEm();

    Long getNaoLidas();
}
