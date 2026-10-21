package com.medcare.senior.domain.relatorio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cabeçalho e resumo do relatório de adesão de um idoso num período (UC-012, RN-012). As doses, uma por linha, são
 * lidas em lotes na hora de gerar o arquivo (não ficam todas na memória).
 *
 * @param de             primeiro dia, inclusivo
 * @param ate            último dia, inclusivo
 * @param geradoEm       agora, no fuso da aplicação
 * @param totais         todas as doses do período, com a taxa de adesão do relatório diário
 * @param porMedicamento uma linha por medicamento avaliado, em ordem de nome
 */
public record RelatorioDeAdesao(Long idosoId,
                                String idosoNome,
                                LocalDate de,
                                LocalDate ate,
                                LocalDateTime geradoEm,
                                TotaisDoDia totais,
                                List<AdesaoDoMedicamento> porMedicamento) {

    /**
     * @param totais doses deste medicamento no período, com a taxa de adesão dele
     */
    public record AdesaoDoMedicamento(Long medicamentoId, String nome, String dosagem, TotaisDoDia totais) {
    }
}
