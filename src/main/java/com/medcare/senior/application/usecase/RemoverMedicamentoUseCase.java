package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoComTratamentoAtivoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

/*
 * =========================================================================================================
 *  DECISÃO DE ARQUITETURA (ADR) — Remoção LÓGICA de Medicamento e Tratamento
 * =========================================================================================================
 *  Status:   Aceita na Fase 2. Substitui a decisão anterior (exclusão física, Fase 1).
 *
 *  Contexto: Tratamento referencia Medicamento, e Dose referencia Tratamento. As doses já registradas são o
 *            histórico de adesão e são imutáveis (RN-008). Apagar o medicamento apagaria (ou travaria pela chave
 *            estrangeira) esse histórico.
 *
 *  Decisão:  "Remover" um medicamento preenche removido_em nele e em todos os tratamentos dele, na mesma
 *            transação. Itens removidos ficam no banco, mas são invisíveis: todas as leituras dos repositórios
 *            os ignoram, e a API responde 404 como se não existissem. Tratamento removido não gera mais doses.
 *            As doses PENDENTES futuras dos tratamentos do medicamento são apagadas (nunca foram registradas);
 *            as doses passadas e as já registradas ficam intactas.
 *
 *  Consequências:
 *    - A estratégia vale DE UMA VEZ para Medicamento e Tratamento; Dose nunca é removida por esta operação.
 *    - Não existe "desfazer remoção" pela API.
 *    - A exclusão de conta (LGPD, ExcluirContaUseCase) é a exceção: apaga FISICAMENTE tudo do cuidador,
 *      inclusive itens já removidos logicamente, e anonimiza as doses registradas. Ver docs/lgpd.md.
 *    - Relacionado: RN-006 (tratamento ativo só é removido com forcarRemocao=true; ver método executar).
 * =========================================================================================================
 */
@Service
public class RemoverMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;
    private final TratamentoRepositoryPort tratamentoRepository;
    private final DoseRepositoryPort doseRepository;
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;
    private final Clock clock;

    public RemoverMedicamentoUseCase(MedicamentoRepositoryPort medicamentoRepository,
                                     IdosoRepositoryPort idosoRepository,
                                     TratamentoRepositoryPort tratamentoRepository,
                                     DoseRepositoryPort doseRepository,
                                     CacheDeRelatorioDiarioPort cacheDeRelatorio,
                                     Clock clock) {
        this.medicamentoRepository = medicamentoRepository;
        this.idosoRepository = idosoRepository;
        this.tratamentoRepository = tratamentoRepository;
        this.doseRepository = doseRepository;
        this.cacheDeRelatorio = cacheDeRelatorio;
        this.clock = clock;
    }

    /**
     * @param id             medicamento a remover
     * @param cuidadorId     ID do cuidador autenticado, extraído do JWT
     * @param forcarRemocao  confirma a remoção mesmo com tratamento ativo (RN-006)
     * @throws MedicamentoComTratamentoAtivoException se houver tratamento ativo e forcarRemocao for false
     */
    @Transactional
    public void executar(Long id, Long cuidadorId, boolean forcarRemocao) {
        // Medicamento já removido responde igual a inexistente (o repositório ignora removidos)
        medicamentoRepository.findById(id)
                .filter(medicamento -> idosoRepository
                        .findByIdAndCuidadorId(medicamento.getIdosoId(), cuidadorId).isPresent())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));

        LocalDateTime agora = LocalDateTime.now(clock);
        LocalDate hoje = agora.toLocalDate();

        // RN-006: tratamento ativo (não removido, sem data de término ou terminando hoje ou depois)
        // exige confirmação explícita
        boolean temTratamentoAtivo = tratamentoRepository.findAllByMedicamentoId(id).stream()
                .anyMatch(tratamento -> tratamento.isAtivo(hoje));
        if (temTratamentoAtivo && !forcarRemocao) {
            throw new MedicamentoComTratamentoAtivoException();
        }

        // Doses pendentes futuras nunca foram registradas e deixam de fazer sentido; as passadas e as registradas
        // ficam como histórico (RN-008)
        doseRepository.deletePendentesFuturasByMedicamentoId(id, agora);
        tratamentoRepository.marcarComoRemovidosPorMedicamento(id, agora);
        medicamentoRepository.marcarComoRemovido(id, agora);
        cacheDeRelatorio.descartarDoCuidador(cuidadorId);
    }
}
