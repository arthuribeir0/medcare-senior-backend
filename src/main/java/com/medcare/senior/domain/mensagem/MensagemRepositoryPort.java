package com.medcare.senior.domain.mensagem;

import com.medcare.senior.domain.historico.Pagina;

import java.time.LocalDateTime;
import java.util.List;

public interface MensagemRepositoryPort {

    Mensagem salvar(Mensagem mensagem);

    /**
     * Mensagens da conversa do paciente, da mais recente para a mais antiga.
     */
    Pagina<Mensagem> findConversa(Long pacienteId, int pagina, int tamanho);

    /**
     * Marca como lidas as mensagens ainda não lidas escritas por {@code remetente} na conversa do paciente (idempotente).
     *
     * @return quantas foram marcadas agora
     */
    int marcarComoLidas(Long pacienteId, Remetente remetente, LocalDateTime agora);

    /**
     * Mensagens ainda não lidas escritas por {@code remetente} na conversa do paciente.
     */
    long contarNaoLidas(Long pacienteId, Remetente remetente);

    /**
     * Mensagens dos pacientes do cuidador ainda não lidas por ele, somando todas as conversas.
     */
    long contarNaoLidasDoCuidador(Long cuidadorId);

    /**
     * Uma conversa por paciente do cuidador (inclusive sem mensagens), a de mensagem mais recente primeiro; as sem
     * mensagens por último, em ordem de nome. Não lidas = mensagens do paciente.
     */
    List<ResumoConversa> findConversasDoCuidador(Long cuidadorId);

    /**
     * A conversa do paciente com o cuidador. Não lidas = mensagens do cuidador.
     */
    ResumoConversa findConversaDoPaciente(Long pacienteId);

    /**
     * Apaga as mensagens de todos os pacientes do cuidador (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
