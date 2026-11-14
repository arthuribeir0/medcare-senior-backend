package com.medcare.senior.domain.notificacao;

import java.time.Duration;
import java.util.Objects;

/**
 * Como o Android deve apresentar um push.
 *
 * @param canalAndroid     canal de notificação criado pelo app (som, vibração e importância vêm dele)
 * @param tag              identificador da notificação no aparelho: outra com a mesma tag SUBSTITUI a anterior em vez
 *                         de empilhar. Nulo = cada push é uma notificação nova
 * @param prioridadeMaxima prioridade máxima e visível na tela bloqueada (lembrete de remédio)
 * @param validade         por quanto tempo o provedor guarda o push se o aparelho estiver sem rede. Nulo = o padrão do
 *                         provedor (até 4 semanas no FCM)
 */
public record OpcoesDeEntrega(String canalAndroid, String tag, boolean prioridadeMaxima, Duration validade) {

    /** Canal dos avisos ao cuidador ("Alertas de dose"). */
    public static final String CANAL_ALERTAS_DOSE = "alertas-dose";

    /** Canal do lembrete de remédio no aparelho do paciente ("Lembrete de remédio"). */
    public static final String CANAL_LEMBRETE_DOSE = "lembrete-dose";

    /** Avisos ao cuidador: canal "alertas-dose", cada aviso é uma notificação. */
    public static final OpcoesDeEntrega PADRAO = new OpcoesDeEntrega(CANAL_ALERTAS_DOSE, null, false, null);

    public OpcoesDeEntrega {
        Objects.requireNonNull(canalAndroid, "canalAndroid");
        if (validade != null && (validade.isNegative() || validade.isZero())) {
            throw new IllegalArgumentException("A validade do push deve ser positiva");
        }
    }
}
