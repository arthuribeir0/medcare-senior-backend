package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga o token de dispositivo FCM do cuidador autenticado (logout do aparelho). Idempotente: sem token gravado, ou
 * com a conta já excluída, nada muda e não há erro.
 */
@Service
@RequiredArgsConstructor
public class RemoverTokenDispositivoUseCase {

    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    @Transactional
    public void executar(Long cuidadorId) {
        cuidadorRepository.removerFcmToken(cuidadorId);
    }
}
