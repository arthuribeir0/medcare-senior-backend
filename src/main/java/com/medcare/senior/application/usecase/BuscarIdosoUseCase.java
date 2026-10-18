package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarIdosoUseCase {

    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param id         idoso a buscar
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws RecursoNaoEncontradoException se o idoso não existir ou for de outro cuidador
     */
    public Idoso executar(Long id, Long cuidadorId) {
        // Idoso de outro cuidador responde igual a idoso inexistente, para não revelar quais IDs existem
        return idosoRepository.findByIdAndCuidadorId(id, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Idoso não encontrado"));
    }
}
