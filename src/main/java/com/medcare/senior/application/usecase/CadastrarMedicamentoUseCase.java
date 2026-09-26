package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CadastrarMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param medicamento dados do novo medicamento (sem id), incluindo o idoso ao qual pertence
     * @param cuidadorId  ID do cuidador autenticado, extraído do JWT
     */
    public Medicamento executar(Medicamento medicamento, Long cuidadorId) {
        // Idoso de outro cuidador responde igual a idoso inexistente, para não revelar quais IDs existem
        if (idosoRepository.findByIdAndCuidadorId(medicamento.getIdosoId(), cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Idoso não encontrado");
        }

        // RN-005
        medicamento.validarCamposObrigatorios();

        medicamento.setId(null);
        return medicamentoRepository.save(medicamento);
    }
}
