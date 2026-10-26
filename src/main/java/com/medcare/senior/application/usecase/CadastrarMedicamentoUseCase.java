package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.domain.medicamento.MedicamentoRepositoryPort;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CadastrarMedicamentoUseCase {

    private final MedicamentoRepositoryPort medicamentoRepository;
    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param medicamento dados do novo medicamento (sem id), incluindo o paciente ao qual pertence
     * @param cuidadorId  ID do cuidador autenticado, extraído do JWT
     */
    public Medicamento executar(Medicamento medicamento, Long cuidadorId) {
        // Paciente de outro cuidador responde igual a paciente inexistente, para não revelar quais IDs existem
        if (pacienteRepository.findByIdAndCuidadorId(medicamento.getPacienteId(), cuidadorId).isEmpty()) {
            throw new RecursoNaoEncontradoException("Paciente não encontrado");
        }

        // RN-005
        medicamento.validarCamposObrigatorios();

        medicamento.setId(null);
        return medicamentoRepository.save(medicamento);
    }
}
