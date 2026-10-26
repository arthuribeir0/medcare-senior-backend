package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dados do paciente autenticado (token PACIENTE) e o nome de quem cuida dele.
 */
@Service
@RequiredArgsConstructor
public class BuscarPacienteAutenticadoUseCase {

    private final PacienteRepositoryPort pacienteRepository;
    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     * @throws SessaoInvalidaException se o paciente não existir mais (token ainda válido de conta excluída)
     */
    public Resultado executar(Long pacienteId) {
        Paciente paciente = pacienteRepository.findById(pacienteId).orElseThrow(SessaoInvalidaException::new);
        String cuidadorNome = cuidadorRepository.findById(paciente.getCuidadorId())
                .map(Cuidador::getNome)
                .orElseThrow(SessaoInvalidaException::new);
        return new Resultado(paciente, cuidadorNome);
    }

    public record Resultado(Paciente paciente, String cuidadorNome) {
    }
}
