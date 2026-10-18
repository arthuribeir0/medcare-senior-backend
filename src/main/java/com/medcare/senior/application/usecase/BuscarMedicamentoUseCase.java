package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param id         medicamento a buscar
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se o medicamento não existir, tiver sido removido ou for de outro cuidador
     */
    public Medicamento executar(Long id, Long cuidadorId) {
        // Medicamento removido ou de outro cuidador responde igual a inexistente (o repositório ignora removidos)
        return medicamentoRepository.findById(id)
                .filter(medicamento -> idosoRepository
                        .findByIdAndCuidadorId(medicamento.getIdosoId(), cuidadorId).isPresent())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Medicamento não encontrado"));
    }
}
