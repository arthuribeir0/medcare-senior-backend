package com.medcare.senior.domain.dose;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Uma dose programada de um tratamento (RN-008).
 *
 * <p>IMUTÁVEL de propósito: não há setters nem métodos que alterem a dose. Doses já registradas (CONFIRMADA_NO_HORARIO,
 * NAO_TOMADA, CONFIRMADA_COM_ATRASO) formam o histórico de adesão e nunca podem mudar. Cada transição cria a
 * versão registrada SOMENTE a partir de uma dose PENDENTE e devolve uma nova instância (ver
 * {@link #marcarComoNaoTomada()} e {@link #confirmar}).
 *
 * <p>{@code tratamentoId} nulo = dose anonimizada: o tratamento foi apagado na exclusão de conta (LGPD).
 *
 * <p>{@code confirmadoPor} só existe nas doses confirmadas (no horário ou com atraso): quem registrou a confirmação.
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
    private final ConfirmadoPor confirmadoPor;

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

    /**
     * Versão confirmada desta dose, em {@code agora}, por {@code quem} (o paciente ou o cuidador dele). Não altera esta instância. O status (CONFIRMADA_NO_HORARIO ou
     * CONFIRMADA_COM_ATRASO) vem da {@link PoliticaDeJanela}; no banco, a gravação também é um UPDATE condicionado a
     * status = 'PENDENTE' ({@link DoseRepositoryPort#registrarSePendente}).
     *
     * @throws TransicaoDeDoseInvalidaException se a dose não estiver PENDENTE
     * @throws IllegalArgumentException         se {@code statusConfirmado} não for CONFIRMADA_NO_HORARIO nem
     *                                          CONFIRMADA_COM_ATRASO, ou se {@code quem} for nulo
     */
    public Dose confirmar(StatusDose statusConfirmado, LocalDateTime agora, ConfirmadoPor quem) {
        if (statusConfirmado != StatusDose.CONFIRMADA_NO_HORARIO && statusConfirmado != StatusDose.CONFIRMADA_COM_ATRASO) {
            throw new IllegalArgumentException("Confirmação só pode gerar CONFIRMADA_NO_HORARIO ou CONFIRMADA_COM_ATRASO");
        }
        if (quem == null) {
            throw new IllegalArgumentException("Informe quem confirmou a dose");
        }
        if (status != StatusDose.PENDENTE) {
            throw new TransicaoDeDoseInvalidaException(id, status, statusConfirmado);
        }
        // Microssegundos: a precisão do TIMESTAMP do PostgreSQL. Sem truncar, a resposta da confirmação mostraria
        // nanossegundos que a próxima leitura do banco não tem mais.
        return Dose.builder()
                .id(id)
                .tratamentoId(tratamentoId)
                .horarioProgramado(horarioProgramado)
                .horaConfirmacao(agora.truncatedTo(ChronoUnit.MICROS))
                .status(statusConfirmado)
                .confirmadoPor(quem)
                .build();
    }
}
