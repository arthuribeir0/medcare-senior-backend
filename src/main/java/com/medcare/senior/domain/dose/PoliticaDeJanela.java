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
 *   <li>avisoEm = horário programado + tolerância (fim do "no horário"; o cuidador é avisado e a dose continua
 *       PENDENTE)</li>
 *   <li>encerraEm = avisoEm + margem pós-aviso (a dose passa a NAO_TOMADA e não pode mais ser confirmada)</li>
 * </ul>
 * A tolerância é informada POR CHAMADA porque pode ser de cada cuidador; {@link #toleranciaPadrao} é o valor
 * global (app.alertas.tolerancia-minutos), usado quando não há outro. Como o encerramento vem depois do aviso por
 * construção, qualquer tolerância positiva é válida.
 *
 * <p>Fronteiras (comparações estritas com isBefore/isAfter):
 * <ul>
 *   <li>exatamente em abreEm: já pode confirmar;</li>
 *   <li>exatamente em avisoEm: a confirmação ainda é no horário (depois, com atraso);</li>
 *   <li>exatamente em encerraEm: a confirmação ainda é aceita (com atraso), e o job já pode encerrar a dose. O
 *       que chegar primeiro ao banco vence, pelo UPDATE condicionado a status = 'PENDENTE'.</li>
 * </ul>
 *
 * @param antecedencia     quanto antes do horário a dose já pode ser confirmada
 * @param toleranciaPadrao tolerância global, quando o cuidador não tem uma própria
 * @param margemPosAviso   quanto depois do aviso a dose ainda pode ser confirmada (com atraso)
 */
public record PoliticaDeJanela(Duration antecedencia, Duration toleranciaPadrao, Duration margemPosAviso) {

    public PoliticaDeJanela {
        exigirPositiva(antecedencia, "A antecedência de confirmação");
        exigirPositiva(margemPosAviso, "A margem depois do aviso");
        exigirPositiva(toleranciaPadrao, "A tolerância");
    }

    private static Duration exigirPositiva(Duration valor, String nome) {
        if (valor == null || valor.isNegative() || valor.isZero()) {
            throw new IllegalArgumentException(nome + " deve ser positiva");
        }
        return valor;
    }

    private static Duration exigirTolerancia(Duration tolerancia) {
        return exigirPositiva(tolerancia, "A tolerância");
    }

    /**
     * Tolerância a aplicar para um cuidador: a própria (em minutos) ou, se ele não tem, a padrão.
     *
     * @throws IllegalArgumentException se a própria não for positiva
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

    /**
     * Horário + tolerância + margem pós-aviso: o último instante em que a dose ainda pode ser confirmada.
     */
    public LocalDateTime encerraEm(LocalDateTime horarioProgramado, Duration tolerancia) {
        return avisoEm(horarioProgramado, tolerancia).plus(margemPosAviso);
    }

    public JanelaDose janela(LocalDateTime horarioProgramado, Duration tolerancia) {
        return new JanelaDose(abreEm(horarioProgramado), avisoEm(horarioProgramado, tolerancia),
                encerraEm(horarioProgramado, tolerancia));
    }

    /**
     * A dose pode ser confirmada de abreEm a encerraEm, inclusive. A tolerância define o encerramento e, em
     * {@link #statusDaConfirmacao}, se a confirmação é no horário ou com atraso.
     */
    public Confirmabilidade confirmabilidade(StatusDose statusGravado, LocalDateTime horarioProgramado,
                                             LocalDateTime agora, Duration tolerancia) {
        if (statusGravado != StatusDose.PENDENTE) {
            return Confirmabilidade.JA_REGISTRADA;
        }
        if (agora.isBefore(abreEm(horarioProgramado))) {
            return Confirmabilidade.MUITO_CEDO;
        }
        if (agora.isAfter(encerraEm(horarioProgramado, tolerancia))) {
            return Confirmabilidade.ENCERRADA;
        }
        return Confirmabilidade.OK;
    }

    public StatusExibicao statusExibicao(StatusDose statusGravado, LocalDateTime horarioProgramado, LocalDateTime agora,
                                         Duration tolerancia) {
        if (statusGravado != StatusDose.PENDENTE) {
            return StatusExibicao.de(statusGravado);
        }
        if (agora.isAfter(encerraEm(horarioProgramado, tolerancia))) {
            return StatusExibicao.NAO_TOMADA;
        }
        if (agora.isAfter(avisoEm(horarioProgramado, tolerancia))) {
            return StatusExibicao.ATRASADA;
        }
        return StatusExibicao.PENDENTE;
    }

    /**
     * Status a gravar numa confirmação feita em {@code agora} (supõe confirmabilidade OK): CONFIRMADA_NO_HORARIO até o
     * fim da tolerância, inclusive; depois, CONFIRMADA_COM_ATRASO.
     */
    public StatusDose statusDaConfirmacao(LocalDateTime horarioProgramado, LocalDateTime agora, Duration tolerancia) {
        return agora.isAfter(avisoEm(horarioProgramado, tolerancia)) ? StatusDose.CONFIRMADA_COM_ATRASO
                : StatusDose.CONFIRMADA_NO_HORARIO;
    }

    /**
     * A dose com a janela, o status de exibição e a confirmabilidade calculados em {@code agora}, com a tolerância
     * informada.
     */
    public DoseAvaliada avaliar(DoseDetalhada dose, LocalDateTime agora, Duration tolerancia) {
        LocalDateTime horario = dose.dose().getHorarioProgramado();
        StatusDose status = dose.dose().getStatus();
        return new DoseAvaliada(dose, janela(horario, tolerancia), statusExibicao(status, horario, agora, tolerancia),
                confirmabilidade(status, horario, agora, tolerancia), tolerancia.toMinutes());
    }
}
