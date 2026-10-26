package com.medcare.senior.domain.relatorio;

import com.medcare.senior.domain.dose.StatusDose;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * Totais do relatório diário (UC-008), pelo status GRAVADO de cada dose. Aderência = (CONFIRMADA_NO_HORARIO + CONFIRMADA_COM_ATRASO)
 * / (total - pendentes) * 100, arredondada a 1 casa (meio para cima); nula quando nenhuma dose do dia está encerrada
 * (o DRS não define outra fórmula).
 */
public record TotaisDoDia(int total,
                          int confirmadasNoHorario,
                          int confirmadasComAtraso,
                          int naoTomadas,
                          int pendentes,
                          Double aderenciaPercentual) {

    public static TotaisDoDia de(Collection<StatusDose> status) {
        return deContagens(contar(status, StatusDose.CONFIRMADA_NO_HORARIO), contar(status, StatusDose.CONFIRMADA_COM_ATRASO),
                contar(status, StatusDose.NAO_TOMADA), contar(status, StatusDose.PENDENTE));
    }

    /**
     * Mesma fórmula a partir das contagens por status (ex.: o relatório de um período, contado no banco).
     */
    public static TotaisDoDia deContagens(int confirmadasNoHorario, int comAtraso, int naoTomadas, int pendentes) {
        int total = confirmadasNoHorario + comAtraso + naoTomadas + pendentes;
        int encerradas = total - pendentes;
        Double aderencia = encerradas == 0 ? null : BigDecimal.valueOf(confirmadasNoHorario + comAtraso)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(encerradas), 1, RoundingMode.HALF_UP)
                .doubleValue();
        return new TotaisDoDia(total, confirmadasNoHorario, comAtraso, naoTomadas, pendentes, aderencia);
    }

    private static int contar(Collection<StatusDose> status, StatusDose alvo) {
        return (int) status.stream().filter(alvo::equals).count();
    }
}
