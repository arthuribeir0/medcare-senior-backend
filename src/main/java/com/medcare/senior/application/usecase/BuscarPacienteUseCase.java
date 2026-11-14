/*
 * Busca um paciente pelo ID, só se for do cuidador autenticado. Paciente de outro cuidador responde igual a
 * inexistente (404), para não revelar quais IDs existem (RN-004).
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarPacienteUseCase {

    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param id         paciente a buscar
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se o paciente não existir ou for de outro cuidador
     */
    public Paciente executar(Long id, Long cuidadorId) {
        // Paciente de outro cuidador responde igual a paciente inexistente, para não revelar quais IDs existem
        return pacienteRepository.findByIdAndCuidadorId(id, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));
    }
}
