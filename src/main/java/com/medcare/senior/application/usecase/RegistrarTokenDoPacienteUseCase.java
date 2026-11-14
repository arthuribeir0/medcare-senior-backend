package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.exception.TokenDispositivoInvalidoException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Registra (ou troca) o token de dispositivo FCM do aparelho do paciente autenticado, usado para o lembrete de dose
 * enviado pelo servidor (reserva do lembrete que o aparelho agenda).
 *
 * <p>O paciente vem SEMPRE do token JWT: nada no corpo escolhe de quem é o aparelho. Um aparelho pertence a um paciente
 * por vez: o mesmo token é retirado de qualquer outro paciente na mesma transação, para que o lembrete de outra pessoa
 * não chegue a este aparelho. O cadastro do cuidador não é tocado (o mesmo celular pode ter os dois perfis).
 */
@Service
@RequiredArgsConstructor
public class RegistrarTokenDoPacienteUseCase {

    // Tamanho da coluna paciente.token_fcm (V18)
    static final int TAMANHO_MAXIMO = 512;

    private final PacienteRepositoryPort pacienteRepository;
    private final Clock clock;

    /**
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     * @throws TokenDispositivoInvalidoException se o token for vazio ou maior que 512 caracteres
     * @throws SessaoInvalidaException           se o paciente não existir mais (ex.: desvinculado)
     */
    @Transactional
    public void executar(Long pacienteId, String fcmToken) {
        String token = fcmToken == null ? "" : fcmToken.trim();
        if (token.isEmpty() || token.length() > TAMANHO_MAXIMO) {
            throw new TokenDispositivoInvalidoException();
        }
        if (!pacienteRepository.atualizarTokenFcm(pacienteId, token, clock.instant())) {
            throw new SessaoInvalidaException();
        }
        pacienteRepository.removerTokenFcmDosOutrosPacientes(token, pacienteId);
    }
}
