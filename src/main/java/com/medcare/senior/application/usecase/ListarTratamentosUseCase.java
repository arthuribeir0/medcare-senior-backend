package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarTratamentosUseCase {

    private final TratamentoRepositoryPort tratamentoRepository;
    private final MedicamentoRepositoryPort medicamentoRepository;
    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param medicamentoId medicamento cujos tratamentos serão listados
     * @param cuidadorId    ID do cuidador autenticado, extraído do JWT
     */
    public List<Tratamento> executar(Long medicamentoId, Long cuidadorId) {
        // Medicamento de outro cuidador responde igual a medicamento inexistente, para não revelar quais IDs existem
        medicamentoRepository.findById(medicamentoId)
                .filter(medicamento -> pacienteRepository
                        .findByIdAndCuidadorId(medicamento.getPacienteId(), cuidadorId).isPresent())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));

        return tratamentoRepository.findAllByMedicamentoId(medicamentoId);
    }
}
