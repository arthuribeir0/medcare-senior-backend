package com.medcare.senior.domain.historico;

import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.exception.ParametroInvalidoException;

import java.time.LocalDate;

/**
 * Filtros do histórico de doses (UC-011), já validados. Todos opcionais, exceto a página.
 *
 * <p>RN-011 (DRS, "Integridade do Período de Consulta e Exportação"): a data de início não pode ser posterior à de
 * fim, e sem data de fim vale a data corrente. Período sem registros não bloqueia: a consulta devolve a página vazia
 * e o app avisa o usuário.
 *
 * @param idosoId       só as doses deste idoso (do cuidador); nulo = todos os idosos do cuidador
 * @param medicamentoId só as doses deste medicamento (UC-011); nulo = todos
 * @param de            primeiro dia, inclusivo; nulo = desde o início
 * @param ate           último dia, inclusivo (já com o padrão da RN-011 aplicado)
 * @param status        só este status gravado; nulo = todos
 * @param pagina        a partir de 0
 * @param tamanho       de 1 a {@link #TAMANHO_MAXIMO}
 */
public record FiltroHistorico(Long idosoId, Long medicamentoId, LocalDate de, LocalDate ate, StatusDose status,
                              int pagina, int tamanho) {

    public static final int TAMANHO_PADRAO = 20;
    public static final int TAMANHO_MAXIMO = 100;

    /**
     * Aplica os padrões (RN-011: sem fim = hoje; tamanho 20; página 0) e valida.
     *
     * @param hoje data corrente no fuso da aplicação
     * @throws ParametroInvalidoException se o início for depois do fim, a página negativa ou o tamanho fora de 1 a 100
     */
    public static FiltroHistorico de(Long idosoId, Long medicamentoId, LocalDate de, LocalDate ate, StatusDose status,
                                     Integer pagina, Integer tamanho, LocalDate hoje) {
        LocalDate fim = ate != null ? ate : hoje;
        if (de != null && de.isAfter(fim)) {
            throw new ParametroInvalidoException("de", "A data de início não pode ser posterior à data de fim");
        }
        int numeroDaPagina = pagina != null ? pagina : 0;
        if (numeroDaPagina < 0) {
            throw new ParametroInvalidoException("page", "A página começa em 0");
        }
        int tamanhoDaPagina = tamanho != null ? tamanho : TAMANHO_PADRAO;
        if (tamanhoDaPagina < 1 || tamanhoDaPagina > TAMANHO_MAXIMO) {
            throw new ParametroInvalidoException("size", "O tamanho da página deve ficar entre 1 e " + TAMANHO_MAXIMO);
        }
        return new FiltroHistorico(idosoId, medicamentoId, de, fim, status, numeroDaPagina, tamanhoDaPagina);
    }
}
