package com.medcare.senior.domain.autenticacao;

/**
 * Resultado de um login (cuidador) ou pareamento (paciente): o JWT e quem está autenticado.
 *
 * @param token     JWT emitido
 * @param usuarioId ID do cuidador (login) ou do paciente (pareamento)
 * @param nome      nome do cuidador ou do paciente
 */
public record SessaoAutenticada(String token, Long usuarioId, String nome) {
}
