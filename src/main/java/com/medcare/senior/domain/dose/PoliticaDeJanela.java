package com.medcare.senior.domain.dose;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Regras de tempo da dose, calculadas no servidor (o app só exibe). Única fonte da verdade usada pelo job de aviso e
 * encerramento, pelo DTO de dose e pela confirmação. Puro: sem Spring e sem relógio próprio; quem chama informa
 * "agora" (vindo do Clock da aplicação) e a tolerância a aplicar.
 *
 * <ul>
 *   <li>abreEm = horário programado - antecedência</li>
 *   <li>avisoEm = horário programado + tolerância (fim do "no prazo"; o cuidador é avisado e a dose continua PENDENTE)</li>
 *   <li>encerraEm = horário programado + encerramento (a dose passa a NAO_TOMADA e não pode mais ser confirmada)</li>
 * </ul>
 * A tolerância é informada POR CHAMADA porque pode ser de cada cuidador; {@link #toleranciaPadrao} é o valor
 * global (app.alertas.tolerancia-minutos), usado quando não há outro. O encerramento é fixo para todos.
 *
 * <p>Os limites exatos contam como dentro da janela de confirmação (comparações estritas com isBefore/isAfter).
 *
 * @param antecedencia     quanto antes do horário a dose já pode ser confirmada
 * @param toleranciaPadrao tolerância global, quando o cuidador não tem uma própria
 * @param encerramento     quanto depois do horário a dose ainda pode ser confirmada (com atraso)
 */
public record PoliticaDeJanela(Duration antecedencia, Duration toleranciaPadrao, Duration encerramento) {

    public PoliticaDeJanela {
        exigirPositiva(antecedencia, "A antecedência de confirmação");
        exigirPositiva(encerramento, "O encerramento da janela");
        validarTolerancia(toleranciaPadrao, encerramento);
    }

    private static void exigirPositiva(Duration valor, String nome) {
        if (valor == null || valor.isNegative() || valor.isZero()) {
            throw new IllegalArgumentException(nome + " deve ser positiva");
        }
    }

    private static void validarTolerancia(Duration tolerancia, Duration encerramento) {
        exigirPositiva(tolerancia, "A tolerância");
        if (tolerancia.compareTo(encerramento) >= 0) {
            throw new IllegalArgumentException("A tolerância deve ser menor que o encerramento da janela de confirmação");
        }
    }

    /**
     * @throws IllegalArgumentException se a tolerância não for positiva ou não for menor que o encerramento
     */
    private Duration exigirTolerancia(Duration tolerancia) {
        validarTolerancia(tolerancia, encerramento);
        return tolerancia;
    }

    /**
     * Tolerância a aplicar para um cuidador: a própria (em minutos) ou, se ele não tem, a padrão.
     *
     * @throws IllegalArgumentException se a própria não for positiva ou não for menor que o encerramento
     */
    public Duration toleranciaEfetiva(Integer minutosDoCuidador) {
        return minutosDoCuidador == null ? toleranciaPadrao : exigirTolerancia(Duration.ofMinutes(minutosDoCuidador));
    }

    public LocalDateTime abreEm(LocalDateTime horarioProgramado) {
        return horarioProgramado.minus(antecedencia);
    }

    public LocalDateTime avisoEm(LocalDateTime horarioProgramado, Duration tolerancia) {
        return horarioProgramado.plus(exigirTolerancia(tolerancia));
    }

    public LocalDateTime encerraEm(LocalDateTime horarioProgramado) {
        return horarioProgramado.plus(encerramento);
    }

    public JanelaDose janela(LocalDateTime horarioProgramado, Duration tolerancia) {
        return new JanelaDose(abreEm(horarioProgramado), avisoEm(horarioProgramado, tolerancia),
                encerraEm(horarioProgramado));
    }

    /**
     * Não depende da tolerância: a dose pode ser confirmada de abreEm a encerraEm (a tolerância só decide se a
     * confirmação é no prazo ou com atraso, em {@link #statusDaConfirmacao}).
     */
    public Confirmabilidade confirmabilidade(StatusDose statusGravado, LocalDateTime horarioProgramado, LocalDateTime agora) {
        if (statusGravado != StatusDose.PENDENTE) {
            return Confirmabilidade.JA_REGISTRADA;
        }
        if (agora.isBefore(abreEm(horarioProgramado))) {
            return Confirmabilidade.MUITO_CEDO;
        }
        if (agora.isAfter(encerraEm(horarioProgramado))) {
            return Confirmabilidade.ENCERRADA;
        }
        return Confirmabilidade.OK;
    }

    public StatusExibicao statusExibicao(StatusDose statusGravado, LocalDateTime horarioProgramado, LocalDateTime agora,
                                         Duration tolerancia) {
        if (statusGravado != StatusDose.PENDENTE) {
            return StatusExibicao.de(statusGravado);
        }
        if (agora.isAfter(encerraEm(horarioProgramado))) {
            return StatusExibicao.NAO_TOMADA;
        }
        if (agora.isAfter(avisoEm(horarioProgramado, tolerancia))) {
            return StatusExibicao.ATRASADA;
        }
        return StatusExibicao.PENDENTE;
    }

    /**
     * Status a gravar numa confirmação feita em {@code agora} (supõe confirmabilidade OK): TOMADA até o fim da
     * tolerância, inclusive; depois, CONFIRMADA_COM_ATRASO.
     */
    public StatusDose statusDaConfirmacao(LocalDateTime horarioProgramado, LocalDateTime agora, Duration tolerancia) {
        return agora.isAfter(avisoEm(horarioProgramado, tolerancia)) ? StatusDose.CONFIRMADA_COM_ATRASO : StatusDose.TOMADA;
    }

    /**
     * A dose com a janela, o status de exibição e a confirmabilidade calculados em {@code agora}, com a tolerância
     * informada.
     */
    public DoseAvaliada avaliar(DoseDetalhada dose, LocalDateTime agora, Duration tolerancia) {
        LocalDateTime horario = dose.dose().getHorarioProgramado();
        StatusDose status = dose.dose().getStatus();
        return new DoseAvaliada(dose, janela(horario, tolerancia), statusExibicao(status, horario, agora, tolerancia),
                confirmabilidade(status, horario, agora), tolerancia.toMinutes());
    }
}
