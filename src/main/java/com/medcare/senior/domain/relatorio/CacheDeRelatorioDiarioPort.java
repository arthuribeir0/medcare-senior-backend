package com.medcare.senior.domain.relatorio;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Cache curto do relatório diário (UC-008), por cuidador, paciente e dia. Quem muda algo que aparece no relatório
 * descarta as entradas afetadas na hora, para o relatório nunca ficar desatualizado depois de uma ação: a confirmação
 * e o encerramento de uma dose descartam as do paciente; as alterações do cuidador (tolerância, medicamento,
 * tratamento), as dele.
 */
public interface CacheDeRelatorioDiarioPort {

    Optional<RelatorioDiario> buscar(Chave chave);

    void guardar(Chave chave, RelatorioDiario relatorio);

    /**
     * Descarta os relatórios do paciente, de qualquer dia e cuidador.
     */
    void descartarDoPaciente(Long pacienteId);

    /**
     * Descarta todos os relatórios pedidos pelo cuidador.
     */
    void descartarDoCuidador(Long cuidadorId);

    /**
     * @param dia o dia do relatório (já resolvido: "hoje" vira a data)
     */
    record Chave(Long cuidadorId, Long pacienteId, LocalDate dia) {
    }
}
