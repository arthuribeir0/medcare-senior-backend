package com.medcare.senior.domain.dose;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Uma dose programada de um tratamento (RN-008).
 *
 * <p>IMUTÁVEL de propósito: não há setters nem métodos que alterem a dose. Doses já registradas (TOMADA,
 * NAO_TOMADA, CONFIRMADA_COM_ATRASO) formam o histórico de adesão e nunca podem mudar. Cada transição cria a
 * versão registrada SOMENTE a partir de uma dose PENDENTE e devolve uma nova instância (ver
 * {@link #marcarComoNaoTomada()}); a confirmação de dose, quando implementada, deve seguir o mesmo padrão.
 *
 * <p>{@code tratamentoId} nulo = dose anonimizada: o tratamento foi apagado na exclusão de conta (LGPD).
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Dose {

    private final Long id;
    private final Long tratamentoId;
    private final LocalDateTime horarioProgramado;
    private final LocalDateTime horaConfirmacao;
    private final StatusDose status;

    /**
     * Nova dose ainda não registrada, criada pelo motor de geração de doses.
     */
    public static Dose pendente(Long tratamentoId, LocalDateTime horarioProgramado) {
        return Dose.builder()
                .tratamentoId(tratamentoId)
                .horarioProgramado(horarioProgramado)
                .status(StatusDose.PENDENTE)
                .build();
    }

    public boolean isRegistrada() {
        return status.isRegistrada();
    }

    /**
     * Versão NAO_TOMADA desta dose, para quando o horário + tolerância passou sem confirmação. Não altera esta
     * instância. Sem hora de confirmação: a dose não foi confirmada.
     *
     * <p>Esta validação é em memória; no banco, a gravação é um UPDATE condicionado a status = 'PENDENTE'
     * ({@link DoseRepositoryPort#registrarSePendente}), para não sobrescrever uma confirmação concorrente.
     *
     * @throws TransicaoDeDoseInvalidaException se a dose não estiver PENDENTE
     */
    public Dose marcarComoNaoTomada() {
        if (status != StatusDose.PENDENTE) {
            throw new TransicaoDeDoseInvalidaException(id, status, StatusDose.NAO_TOMADA);
        }
        return Dose.builder()
                .id(id)
                .tratamentoId(tratamentoId)
                .horarioProgramado(horarioProgramado)
                .status(StatusDose.NAO_TOMADA)
                .build();
    }
}
