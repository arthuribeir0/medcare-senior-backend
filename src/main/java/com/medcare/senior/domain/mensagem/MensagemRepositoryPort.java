package com.medcare.senior.domain.mensagem;

import com.medcare.senior.domain.historico.Pagina;

import java.time.LocalDateTime;
import java.util.List;

public interface MensagemRepositoryPort {

    Mensagem salvar(Mensagem mensagem);

    /**
     * Mensagens da conversa do idoso, da mais recente para a mais antiga.
     */
    Pagina<Mensagem> findConversa(Long idosoId, int pagina, int tamanho);

    /**
     * Marca como lidas as mensagens ainda não lidas escritas por {@code remetente} na conversa do idoso (idempotente).
     *
     * @return quantas foram marcadas agora
     */
    int marcarComoLidas(Long idosoId, Remetente remetente, LocalDateTime agora);

    /**
     * Mensagens ainda não lidas escritas por {@code remetente} na conversa do idoso.
     */
    long contarNaoLidas(Long idosoId, Remetente remetente);

    /**
     * Mensagens dos idosos do cuidador ainda não lidas por ele, somando todas as conversas.
     */
    long contarNaoLidasDoCuidador(Long cuidadorId);

    /**
     * Uma conversa por idoso do cuidador (inclusive sem mensagens), a de mensagem mais recente primeiro; as sem
     * mensagens por último, em ordem de nome. Não lidas = mensagens do idoso.
     */
    List<ResumoConversa> findConversasDoCuidador(Long cuidadorId);

    /**
     * A conversa do idoso com o cuidador. Não lidas = mensagens do cuidador.
     */
    ResumoConversa findConversaDoIdoso(Long idosoId);

    /**
     * Apaga as mensagens de todos os idosos do cuidador (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
