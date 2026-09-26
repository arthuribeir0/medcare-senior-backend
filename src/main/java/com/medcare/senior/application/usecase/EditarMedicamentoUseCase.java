package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EditarMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;

    /**
     * Substitui todos os dados do medicamento (semântica de PUT).
     *
     * @param id         medicamento a editar
     * @param dados      novos dados; o idosoId pode mudar, desde que o novo idoso também pertença ao cuidador
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public Medicamento executar(Long id, Medicamento dados, Long cuidadorId) {
        Medicamento atual = medicamentoRepository.findById(id)
                .filter(medicamento -> pertenceAoCuidador(medicamento.getIdosoId(), cuidadorId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));

        if (!dados.getIdosoId().equals(atual.getIdosoId()) && !pertenceAoCuidador(dados.getIdosoId(), cuidadorId)) {
            throw new RecursoNaoEncontradoException("Idoso não encontrado");
        }

        // RN-005
        dados.validarCamposObrigatorios();

        dados.setId(atual.getId());
        return medicamentoRepository.save(dados);
    }

    private boolean pertenceAoCuidador(Long idosoId, Long cuidadorId) {
        return idosoRepository.findByIdAndCuidadorId(idosoId, cuidadorId).isPresent();
    }
}
