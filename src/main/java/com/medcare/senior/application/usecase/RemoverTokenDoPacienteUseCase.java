package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apaga o token de dispositivo FCM do paciente autenticado (o app sai da conta ou deixa de ser o aparelho do paciente).
 * Idempotente: sem token gravado nada muda e não há erro.
 */
@Service
@RequiredArgsConstructor
public class RemoverTokenDoPacienteUseCase {

    private final PacienteRepositoryPort pacienteRepository;

    /**
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     */
    @Transactional
    public void executar(Long pacienteId) {
        pacienteRepository.removerTokenFcm(pacienteId);
    }
}
