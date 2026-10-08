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
 * NAO_TOMADA, CONFIRMADA_COM_ATRASO) formam o histórico de adesão e nunca podem mudar. Quando a confirmação de
 * dose for implementada, ela deve criar a versão registrada SOMENTE a partir de uma dose PENDENTE (ex.: um
 * método que valide {@code status == PENDENTE} e devolva uma nova instância), nunca alterar uma dose registrada.
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
}
