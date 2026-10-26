package com.medcare.senior.domain.mensagem;

import java.time.LocalDateTime;

/**
 * Uma conversa na lista de conversas: o paciente, a última mensagem (se houver) e quantas mensagens o leitor ainda não leu.
 *
 * @param ultimaMensagem {@code null} se a conversa ainda não tem mensagens
 * @param naoLidas       mensagens do outro lado ainda não lidas pelo leitor
 */
public record ResumoConversa(Long pacienteId, String pacienteNome, UltimaMensagem ultimaMensagem, long naoLidas) {

    public record UltimaMensagem(String conteudo, Remetente remetente, LocalDateTime criadaEm) {
    }
}
