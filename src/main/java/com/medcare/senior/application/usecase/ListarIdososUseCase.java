package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListarIdososUseCase {

    private final IdosoRepositoryPort idosoRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @return os idosos do cuidador, em ordem alfabética de nome (sem diferenciar maiúsculas de minúsculas)
     */
    public List<Idoso> executar(Long cuidadorId) {
        return idosoRepository.findAllByCuidadorId(cuidadorId);
    }
}
