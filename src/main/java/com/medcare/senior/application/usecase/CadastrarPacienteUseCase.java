package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.PacienteJaCadastradoException;
import com.medcare.senior.domain.exception.EmailNaoVerificadoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
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

        Cuidador cuidador = cuidadorRepository.findById(cuidadorId).orElseThrow(SessaoInvalidaException::doCuidador);
        // Só quem confirmou o e-mail cadastra pacientes (dados de saúde de terceiros numa conta sem dono comprovado, não)
        if (!cuidador.isEmailVerificado()) {
            throw new EmailNaoVerificadoException();
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
