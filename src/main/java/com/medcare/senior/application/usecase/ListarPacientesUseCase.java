package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarPacientesUseCase {

    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @return os pacientes do cuidador, em ordem alfabética de nome (sem diferenciar maiúsculas de minúsculas)
     */
    public List<Paciente> executar(Long cuidadorId) {
        return pacienteRepository.findAllByCuidadorId(cuidadorId);
    }
}
