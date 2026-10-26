package com.medcare.senior.domain.tratamento;

/**
 * Horário do tratamento que cai hoje entre o minuto atual e daqui a menos de 5 minutos: a dose de hoje nunca seria
 * gerada (ou nasceria já vencendo), e o cuidador não veria nada.
 */
public class HorarioMuitoProximoException extends RuntimeException {

    public static final String MENSAGEM = "Não é possível cadastrar um horário que seja agora ou daqui a menos de 5 "
            + "minutos. Escolha um horário com pelo menos 5 minutos de diferença do horário atual.";

    public HorarioMuitoProximoException() {
        super(MENSAGEM);
    }
}
