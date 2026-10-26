package com.medcare.senior.domain.usuario;

import java.util.Optional;

public interface CuidadorRepositoryPort {

    /**
     * @throws com.medcare.senior.domain.exception.EmailJaCadastradoException se o e-mail já estiver em uso (RN-001)
     */
    Cuidador save(Cuidador cuidador);

    Optional<Cuidador> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsById(Long id);

    Optional<Cuidador> findById(Long id);

    /**
     * Grava o token de dispositivo (FCM) do cuidador em um UPDATE só dessa coluna.
     *
     * @return {@code true} se o cuidador existe e foi atualizado
     */
    boolean atualizarFcmToken(Long cuidadorId, String fcmToken);

    /**
     * Tira o token de dispositivo de todos os OUTROS cuidadores que o tenham. Um aparelho pertence a uma conta por
     * vez: sem isso, quem trocou de conta no mesmo celular continuaria recebendo avisos (com nomes de pacientes e
     * medicamentos) da conta anterior.
     */
    void removerFcmTokenDosOutrosCuidadores(String fcmToken, Long cuidadorId);

    /**
     * Apaga o token de dispositivo do cuidador SOMENTE se ele ainda for {@code fcmToken} (o provedor de push disse
     * que é inválido). Se o cuidador registrou um token novo nesse meio tempo, o novo é mantido.
     */
    void removerFcmTokenSeIgual(Long cuidadorId, String fcmToken);

    /**
     * Apaga o token de dispositivo do cuidador, qualquer que seja (logout do aparelho). Sem token gravado, nada muda.
     */
    void removerFcmToken(Long cuidadorId);

    void deleteById(Long id);
}
