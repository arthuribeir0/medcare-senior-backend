package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarMedicamentosUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param idosoId    idoso cujos medicamentos serão listados
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public List<Medicamento> executar(Long idosoId, Long cuidadorId) {
        // Idoso de outro cuidador responde igual a idoso inexistente, para não revelar quais IDs existem
        if (idosoRepository.findByIdAndCuidadorId(idosoId, cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Idoso não encontrado");
        }

        return medicamentoRepository.findAllByIdosoId(idosoId);
    }
}
