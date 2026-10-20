package com.medcare.senior.domain.mensagem;

import com.medcare.senior.domain.exception.ParametroInvalidoException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Mensagem da conversa entre um idoso e o seu cuidador (UC-010). O conteúdo é dado pessoal: nunca vai para o log.
 *
 * @param idosoId   idoso da conversa
 * @param remetente quem escreveu
 * @param conteudo  texto de 1 a {@link #TAMANHO_MAXIMO} caracteres, sem espaços nas pontas
 * @param criadaEm  envio, no fuso da aplicação
 * @param lidaEm    quando o destinatário abriu a conversa; {@code null} = não lida
 */
public record Mensagem(Long id, Long idosoId, Remetente remetente, String conteudo, LocalDateTime criadaEm,
                       LocalDateTime lidaEm) {

    public static final int TAMANHO_MAXIMO = 500;

    /**
     * Mensagem nova, ainda sem ID e não lida. Os espaços nas pontas são retirados; o tamanho é contado em caracteres
     * (como o CHECK do banco), não em unidades UTF-16.
     *
     * @throws ParametroInvalidoException se o conteúdo for vazio, só espaços ou maior que {@link #TAMANHO_MAXIMO}
     */
    public static Mensagem nova(Long idosoId, Remetente remetente, String conteudo, LocalDateTime agora) {
        String texto = conteudo == null ? "" : conteudo.strip();
        if (texto.isEmpty()) {
            throw new ParametroInvalidoException("conteudo", "A mensagem não pode ser vazia");
        }
        if (texto.codePointCount(0, texto.length()) > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException("conteudo", "A mensagem deve ter no máximo " + TAMANHO_MAXIMO
                    + " caracteres");
        }
        // Mesma precisão do TIMESTAMP do PostgreSQL: a resposta do envio mostra o que foi gravado
        return new Mensagem(null, idosoId, remetente, texto, agora.truncatedTo(ChronoUnit.MICROS), null);
    }
}
