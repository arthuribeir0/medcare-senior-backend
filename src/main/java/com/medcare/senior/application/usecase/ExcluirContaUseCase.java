package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * =========================================================================================================
 *  POLÍTICA DE EXCLUSÃO DE CONTA (LGPD, art. 18, VI) — ver também docs/lgpd.md
 * =========================================================================================================
 *  - Dados pessoais são REMOVIDOS fisicamente: o cuidador e todos os idosos, medicamentos, tratamentos e
 *    códigos de vínculo ligados a ele, INCLUSIVE medicamentos e tratamentos já removidos logicamente.
 *  - Doses PENDENTES (nunca registradas) são apagadas.
 *  - Doses já registradas (TOMADA, NAO_TOMADA, CONFIRMADA_COM_ATRASO) NÃO são apagadas: são ANONIMIZADAS.
 *    Quando os tratamentos são apagados, a FK dose.tratamento_id (ON DELETE SET NULL) zera o vínculo, e a dose
 *    passa a não identificar ninguém (só horário programado, hora de confirmação e status).
 *  - Tudo acontece em uma única transação: ou a conta inteira some, ou nada muda.
 *  - Ordem (respeita as chaves estrangeiras):
 *    códigos de vínculo -> doses pendentes -> tratamentos -> medicamentos -> idosos -> cuidador.
 * =========================================================================================================
 */
@Service
@RequiredArgsConstructor
public class ExcluirContaUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;
    private final IdosoRepositoryPort idosoRepository;
    private final MedicamentoRepositoryPort medicamentoRepository;
    private final TratamentoRepositoryPort tratamentoRepository;
    private final DoseRepositoryPort doseRepository;
    private final CodigoVinculoRepositoryPort codigoVinculoRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se a conta não existir (por exemplo, já excluída com o mesmo token)
     */
    @Transactional
    public void executar(Long cuidadorId) {
        if (!cuidadorRepository.existsById(cuidadorId)) {
            throw new RecursoNaoEncontradoException("Cuidador não encontrado");
        }

        codigoVinculoRepository.deleteAllByCuidadorId(cuidadorId);
        doseRepository.deletePendentesByCuidadorId(cuidadorId);
        // As doses registradas restantes são anonimizadas pelo banco (ON DELETE SET NULL) neste passo
        tratamentoRepository.deleteAllByCuidadorId(cuidadorId);
        medicamentoRepository.deleteAllByCuidadorId(cuidadorId);
        idosoRepository.deleteAllByCuidadorId(cuidadorId);
        cuidadorRepository.deleteById(cuidadorId);
    }
}
