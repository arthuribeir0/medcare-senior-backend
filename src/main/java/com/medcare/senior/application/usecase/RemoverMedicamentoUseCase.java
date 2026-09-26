package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/*
 * =========================================================================================================
 *  DECISÃO DE ARQUITETURA (ADR) — Exclusão física de Medicamento
 * =========================================================================================================
 *  Status:   Aceita para a Fase 1. Deve ser revisitada no início da Fase 2 (Núcleo de Negócio).
 *
 *  Contexto: Hoje Medicamento não é referenciado por nenhuma outra tabela. Na Fase 2 surgirão Tratamento
 *            e Dose, que vão referenciar Medicamento por chave estrangeira; a partir daí, um DELETE físico
 *            de um medicamento com histórico será bloqueado pelo banco ou apagaria histórico clínico.
 *
 *  Decisão:  A exclusão é FÍSICA (DELETE) por decisão consciente nesta fase do projeto. Não há soft delete.
 *
 *  Consequências / pendência:
 *    - Quando a Fase 2 for implementada, a estratégia de exclusão precisa ser revisitada de forma
 *      CONSISTENTE para todas as entidades envolvidas: Medicamento, Tratamento e Dose.
 *    - Se soft delete for adotado, a mudança deve ser feita DE UMA VEZ SÓ para todas elas (migration,
 *      entidades, consultas e use cases), e não entidade por entidade, para evitar regras misturadas
 *      (ex.: medicamento "apagado" logicamente com doses apagadas fisicamente, ou vice-versa).
 *    - Relacionado: RN-006 (ver TODO no método executar).
 * =========================================================================================================
 */
@Service
@RequiredArgsConstructor
public class RemoverMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param id             medicamento a remover
     * @param cuidadorId     ID do cuidador autenticado, extraído do JWT
     * @param forcarRemocao  confirma a remoção mesmo com tratamento ativo (RN-006; ainda sem efeito, ver TODO)
     */
    public void executar(Long id, Long cuidadorId, boolean forcarRemocao) {
        medicamentoRepository.findById(id)
                .filter(medicamento -> idosoRepository
                        .findByIdAndCuidadorId(medicamento.getIdosoId(), cuidadorId).isPresent())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));

        // TODO RN-006: quando a entidade Tratamento existir (Fase 2 do plano), verificar aqui se há
        //  tratamento ativo vinculado a este medicamento antes de permitir a remoção sem forcarRemocao=true
        //  (lançando MedicamentoComTratamentoAtivoException, já mapeada para HTTP 409);
        //  por ora, a remoção é sempre permitida.

        medicamentoRepository.deleteById(id);
    }
}
