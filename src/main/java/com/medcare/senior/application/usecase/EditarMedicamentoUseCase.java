/*
 * UC-004: edição de medicamento (semântica de PUT: substitui todos os dados). Só o dono edita; o paciente pode
 * mudar, desde que o novo também seja do cuidador. Revalida os campos obrigatórios (RN-005) e descarta o cache do
 * relatório diário, pois nome, dosagem e cor aparecem nele.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.domain.relatorio.CacheDeRelatorioDiarioPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EditarMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final CacheDeRelatorioDiarioPort cacheDeRelatorio;

    /**
     * Substitui todos os dados do medicamento (semântica de PUT).
     *
     * @param id         medicamento a editar
     * @param dados      novos dados; o pacienteId pode mudar, desde que o novo paciente também pertença ao cuidador
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public Medicamento executar(Long id, Medicamento dados, Long cuidadorId) {
        Medicamento atual = medicamentoRepository.findById(id)
                .filter(medicamento -> pertenceAoCuidador(medicamento.getPacienteId(), cuidadorId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));

        if (!dados.getPacienteId().equals(atual.getPacienteId()) && !pertenceAoCuidador(dados.getPacienteId(), cuidadorId)) {
            throw new RecursoNaoEncontradoException("Paciente não encontrado");
        }

        // RN-005
        dados.validarCamposObrigatorios();

        dados.setId(atual.getId());
        Medicamento salvo = medicamentoRepository.save(dados);
        // Nome, dosagem e cor aparecem nas doses do relatório diário
        cacheDeRelatorio.descartarDoCuidador(cuidadorId);
        return salvo;
    }

    private boolean pertenceAoCuidador(Long pacienteId, Long cuidadorId) {
        return pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId).isPresent();
    }
}
