package com.medcare.senior.domain.dose;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Regras de tempo da confirmação de dose, calculadas no servidor (o app só exibe). Puro: sem Spring e sem relógio
 * próprio; quem chama informa "agora" (vindo do Clock da aplicação).
 *
 * <ul>
 *   <li>abreEm = horário programado - antecedência</li>
 *   <li>avisoEm = horário programado + tolerância</li>
 *   <li>encerraEm = horário programado + encerramento</li>
 * </ul>
 * Os limites exatos contam como dentro da janela (comparações estritas com isBefore/isAfter).
 *
 * @param antecedencia quanto antes do horário a dose já pode ser confirmada
 * @param tolerancia   quanto depois do horário a dose ainda é "no prazo"
 * @param encerramento quanto depois do horário a dose ainda pode ser confirmada (com atraso)
 */
public record PoliticaDeJanela(Duration antecedencia, Duration tolerancia, Duration encerramento) {

    public PoliticaDeJanela {
        exigirPositiva(antecedencia, "A antecedência de confirmação");
        exigirPositiva(tolerancia, "A tolerância");
        exigirPositiva(encerramento, "O encerramento da janela");
        if (tolerancia.compareTo(encerramento) >= 0) {
            throw new IllegalArgumentException("A tolerância deve ser menor que o encerramento da janela de confirmação");
        }
    }

    private static void exigirPositiva(Duration valor, String nome) {
        if (valor == null || valor.isNegative() || valor.isZero()) {
            throw new IllegalArgumentException(nome + " deve ser positiva");
        }
    }

    public JanelaDose janela(LocalDateTime horarioProgramado) {
        return new JanelaDose(horarioProgramado.minus(antecedencia), horarioProgramado.plus(tolerancia),
                horarioProgramado.plus(encerramento));
    }

    public Confirmabilidade confirmabilidade(StatusDose statusGravado, LocalDateTime horarioProgramado, LocalDateTime agora) {
        if (statusGravado != StatusDose.PENDENTE) {
            return Confirmabilidade.JA_REGISTRADA;
        }
        JanelaDose janela = janela(horarioProgramado);
        if (agora.isBefore(janela.abreEm())) {
            return Confirmabilidade.MUITO_CEDO;
        }
        if (agora.isAfter(janela.encerraEm())) {
            return Confirmabilidade.ENCERRADA;
        }
        return Confirmabilidade.OK;
    }

    public StatusExibicao statusExibicao(StatusDose statusGravado, LocalDateTime horarioProgramado, LocalDateTime agora) {
        if (statusGravado != StatusDose.PENDENTE) {
            return StatusExibicao.de(statusGravado);
        }
        JanelaDose janela = janela(horarioProgramado);
        if (agora.isAfter(janela.encerraEm())) {
            return StatusExibicao.NAO_TOMADA;
        }
        if (agora.isAfter(janela.avisoEm())) {
            return StatusExibicao.ATRASADA;
        }
        return StatusExibicao.PENDENTE;
    }

    /**
     * Status a gravar numa confirmação feita em {@code agora} (supõe confirmabilidade OK): TOMADA até o fim da
     * tolerância, inclusive; depois, CONFIRMADA_COM_ATRASO.
     */
    public StatusDose statusDaConfirmacao(LocalDateTime horarioProgramado, LocalDateTime agora) {
        return agora.isAfter(janela(horarioProgramado).avisoEm()) ? StatusDose.CONFIRMADA_COM_ATRASO : StatusDose.TOMADA;
    }

    public long toleranciaMinutos() {
        return tolerancia.toMinutes();
    }

    /**
     * A dose com a janela, o status de exibição e a confirmabilidade calculados em {@code agora}.
     */
    public DoseAvaliada avaliar(DoseDetalhada dose, LocalDateTime agora) {
        LocalDateTime horario = dose.dose().getHorarioProgramado();
        StatusDose status = dose.dose().getStatus();
        return new DoseAvaliada(dose, janela(horario), statusExibicao(status, horario, agora),
                confirmabilidade(status, horario, agora), toleranciaMinutos());
    }
}
