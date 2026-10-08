package com.medcare.senior.domain.usuario;

import java.time.LocalDateTime;
import java.util.Optional;

public interface CodigoVinculoRepositoryPort {

    CodigoVinculo save(CodigoVinculo codigoVinculo);

    /**
     * Indica se já existe um código ativo (não usado e não expirado em {@code agora}) com esse valor.
     */
    boolean existsCodigoAtivo(String codigoUnico, LocalDateTime agora);

    /**
     * Busca o registro mais recente com esse valor de código. O mesmo valor de 6 dígitos pode se repetir
     * ao longo do tempo (códigos usados ou expirados não bloqueiam o valor), mas só pode existir um ativo
     * por vez, e ele é sempre o mais recente.
     */
    Optional<CodigoVinculo> findMaisRecenteByCodigoUnico(String codigoUnico);

    /**
     * Marca o código como usado de forma atômica, somente se ele ainda não foi usado e não expirou.
     *
     * @return {@code true} se esta chamada marcou o código; {@code false} se outra requisição já o havia
     * consumido (ou se ele expirou nesse meio-tempo)
     */
    boolean marcarComoUsado(Long id, LocalDateTime agora);

    /**
     * Remove todos os códigos de vínculo gerados pelo cuidador (exclusão de conta, LGPD).
     */
    void deleteAllByCuidadorId(Long cuidadorId);
}
