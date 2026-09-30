package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.IdosoJaCadastradoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CadastrarIdosoUseCase {

    private final IdosoRepositoryPort idosoRepository;
    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT (nunca do corpo da requisição)
     */
    public Idoso executar(String nome, LocalDate dataNascimento, String telefone, Long cuidadorId) {
        String nomeNormalizado = nome.trim();

        // RN-003
        if (idosoRepository.existsByCuidadorIdAndNomeAndDataNascimento(cuidadorId, nomeNormalizado, dataNascimento)) {
            throw new IdosoJaCadastradoException();
        }

        if (!cuidadorRepository.existsById(cuidadorId)) {
            throw new RecursoNaoEncontradoException("Cuidador não encontrado");
        }

        Idoso idoso = Idoso.builder()
                .nome(nomeNormalizado)
                .dataNascimento(dataNascimento)
                .telefone(telefone)
                .cuidadorId(cuidadorId)
                .build();

        // Cadastro concorrente duplicado é convertido em IdosoJaCadastradoException pelo adapter
        return idosoRepository.save(idoso);
    }
}
