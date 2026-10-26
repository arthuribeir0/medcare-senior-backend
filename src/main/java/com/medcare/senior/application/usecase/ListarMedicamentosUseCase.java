package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarMedicamentosUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param pacienteId    paciente cujos medicamentos serão listados
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public List<Medicamento> executar(Long pacienteId, Long cuidadorId) {
        // Paciente de outro cuidador responde igual a paciente inexistente, para não revelar quais IDs existem
        if (pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Paciente não encontrado");
        }

        return medicamentoRepository.findAllByPacienteId(pacienteId);
    }
}
