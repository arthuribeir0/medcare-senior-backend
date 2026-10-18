package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.exception.TokenDispositivoInvalidoException;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra (ou troca) o token de dispositivo FCM do cuidador autenticado, usado para os avisos push.
 *
 * <p>Um aparelho pertence a uma conta por vez: o mesmo token é retirado de qualquer outro cuidador na mesma
 * transação, para que avisos da conta anterior não cheguem ao aparelho de outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class RegistrarTokenDispositivoUseCase {

    // Tamanho da coluna cuidador.fcm_token (V10)
    static final int TAMANHO_MAXIMO = 512;

    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     * @throws TokenDispositivoInvalidoException se o token for vazio ou maior que 512 caracteres
     * @throws SessaoInvalidaException           se a conta não existir (ex.: excluída com o token ainda válido)
     */
    @Transactional
    public void executar(Long cuidadorId, String fcmToken) {
        String token = fcmToken == null ? "" : fcmToken.trim();
        if (token.isEmpty() || token.length() > TAMANHO_MAXIMO) {
            throw new TokenDispositivoInvalidoException();
        }

        if (!cuidadorRepository.atualizarFcmToken(cuidadorId, token)) {
            throw SessaoInvalidaException.doCuidador();
        }
        cuidadorRepository.removerFcmTokenDosOutrosCuidadores(token, cuidadorId);
    }
}
