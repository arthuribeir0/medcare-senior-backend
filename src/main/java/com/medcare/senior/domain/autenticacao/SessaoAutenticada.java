package com.medcare.senior.domain.autenticacao;

/**
 * Resultado de um login (cuidador) ou pareamento (idoso): o JWT e quem está autenticado.
 *
 * @param token     JWT emitido
 * @param usuarioId ID do cuidador (login) ou do idoso (pareamento)
 * @param nome      nome do cuidador ou do idoso
 */
public record SessaoAutenticada(String token, Long usuarioId, String nome) {
}
