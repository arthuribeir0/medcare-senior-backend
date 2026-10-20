package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuscarCuidadorUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws SessaoInvalidaException se a conta não existir (ex.: excluída com o token ainda válido)
     */
    public Cuidador executar(Long cuidadorId) {
        return cuidadorRepository.findById(cuidadorId)
                .orElseThrow(SessaoInvalidaException::doCuidador);
    }
}
