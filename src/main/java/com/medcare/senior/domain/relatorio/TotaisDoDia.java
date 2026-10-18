package com.medcare.senior.domain.relatorio;

import com.medcare.senior.domain.dose.StatusDose;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Totais do relatório diário (UC-008), pelo status GRAVADO de cada dose. Aderência = (TOMADA + CONFIRMADA_COM_ATRASO)
 * / (total - pendentes) * 100, arredondada a 1 casa (meio para cima); nula quando nenhuma dose do dia está encerrada
 * (o DRS não define outra fórmula).
 */
public record TotaisDoDia(int total,
                          int tomadas,
                          int confirmadasComAtraso,
                          int naoTomadas,
                          int pendentes,
                          Double aderenciaPercentual) {

    public static TotaisDoDia de(Collection<StatusDose> status) {
        int tomadas = contar(status, StatusDose.TOMADA);
        int comAtraso = contar(status, StatusDose.CONFIRMADA_COM_ATRASO);
        int naoTomadas = contar(status, StatusDose.NAO_TOMADA);
        int pendentes = contar(status, StatusDose.PENDENTE);
        int encerradas = status.size() - pendentes;
        Double aderencia = encerradas == 0 ? null : BigDecimal.valueOf(tomadas + comAtraso)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(encerradas), 1, RoundingMode.HALF_UP)
                .doubleValue();
        return new TotaisDoDia(status.size(), tomadas, comAtraso, naoTomadas, pendentes, aderencia);
    }

    private static int contar(Collection<StatusDose> status, StatusDose alvo) {
        return (int) status.stream().filter(alvo::equals).count();
    }
}
