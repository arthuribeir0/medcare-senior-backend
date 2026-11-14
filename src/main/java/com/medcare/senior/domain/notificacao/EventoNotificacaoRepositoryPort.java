package com.medcare.senior.domain.notificacao;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Eventos de notificação de uma dose: o aviso ao cuidador de dose não confirmada (RN-010) e o lembrete ao paciente.
 * Cada operação é atômica e condicionada ao estado esperado, para que o job de aviso, o de lembrete e o de retentativa
 * nunca enviem a mesma notificação duas vezes.
 */
public interface EventoNotificacaoRepositoryPort {

    /**
     * Cria o evento PENDENTE da dose, do tipo informado, com zero tentativas, SOMENTE se ela ainda não tiver um desse
     * tipo ({@code INSERT ... ON CONFLICT (dose_id, tipo) DO NOTHING}).
     *
     * @return o id do evento criado, ou vazio se a dose já tinha evento desse tipo (outra execução chegou antes)
     */
    Optional<Long> criarSeAusente(Long doseId, TipoEventoNotificacao tipo);

    /**
     * Reivindica a próxima tentativa de envio: soma 1 em tentativas e grava {@code agora} em ultima_tentativa,
     * SOMENTE se o evento ainda estiver PENDENTE com {@code tentativasEsperadas}. Só quem reivindicou envia.
     *
     * @return true se esta chamada ganhou a tentativa
     */
    boolean reivindicar(Long eventoId, int tentativasEsperadas, LocalDateTime agora);

    /**
     * Até {@code limite} eventos PENDENTES (de qualquer tipo) prontos para a retentativa (RN-010), dos que esperam há
     * mais tempo para os mais recentes:
     * <ul>
     *   <li>nunca tentados (tentativas = 0, ultima_tentativa nula): na hora (queda entre o INSERT e o envio);</li>
     *   <li>com tentativas entre 1 e {@code maxTentativas - 1}: quando agora >= ultima_tentativa +
     *       intervaloBase * 2^(tentativas - 1) (com base de 5 min: esperas de 5, 10, 20 e 40 min);</li>
     *   <li>com tentativas >= {@code maxTentativas}: sempre (só falta registrar o esgotamento).</li>
     * </ul>
     */
    List<EventoParaRetentativa> findParaRetentativa(LocalDateTime agora, int maxTentativas, Duration intervaloBase,
                                                    int limite);

    /**
     * PENDENTE -> ENVIADO. Não faz nada se o evento já tiver saído de PENDENTE.
     */
    void marcarEnviado(Long eventoId);

    /**
     * PENDENTE -> FALHA com o motivo. Não faz nada se o evento já tiver saído de PENDENTE.
     */
    void marcarFalha(Long eventoId, MotivoFalhaNotificacao motivo);

    /**
     * Apaga os eventos de todas as doses (de qualquer status) dos tratamentos do cuidador (exclusão de conta, LGPD).
     * Precisa rodar antes de os tratamentos serem apagados, enquanto as doses ainda apontam para eles.
     */
    void deleteByCuidadorId(Long cuidadorId);

    /**
     * Apaga os eventos de todas as doses (de qualquer status) dos tratamentos do paciente (desvinculação do paciente).
     * Precisa rodar antes de os tratamentos serem apagados, enquanto as doses ainda apontam para eles.
     */
    void deleteByPacienteId(Long pacienteId);
}
