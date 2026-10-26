package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.PacienteJaCadastradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CadastrarPacienteUseCase {

    private final PacienteRepositoryPort pacienteRepository;
    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT (nunca do corpo da requisição)
     */
    public Paciente executar(String nome, LocalDate dataNascimento, String telefone, Long cuidadorId) {
        String nomeNormalizado = nome.trim();

        // RN-003
        if (pacienteRepository.existsByCuidadorIdAndNomeAndDataNascimento(cuidadorId, nomeNormalizado, dataNascimento)) {
            throw new PacienteJaCadastradoException();
        }

        if (!cuidadorRepository.existsById(cuidadorId)) {
            throw SessaoInvalidaException.doCuidador();
        }

        Paciente paciente = Paciente.builder()
                .nome(nomeNormalizado)
                .dataNascimento(dataNascimento)
                .telefone(telefone)
                .cuidadorId(cuidadorId)
                .build();

        // Cadastro concorrente duplicado é convertido em PacienteJaCadastradoException pelo adapter
        return pacienteRepository.save(paciente);
    }
}
