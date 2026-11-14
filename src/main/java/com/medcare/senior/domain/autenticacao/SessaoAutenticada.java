package com.medcare.senior.domain.autenticacao;

/**
 * Resultado de um login (cuidador) ou pareamento (paciente): o JWT e quem está autenticado.
 *
 * @param token     JWT emitido
 * @param usuarioId ID do cuidador (login) ou do paciente (pareamento)
 * @param nome      nome do cuidador ou do paciente
 * @param emailVerificado só no login do cuidador: o e-mail já foi confirmado ({@code null} no pareamento)
 */
public record SessaoAutenticada(String token, Long usuarioId, String nome, Boolean emailVerificado) {

    /** Pareamento do paciente: não há e-mail. */
    public SessaoAutenticada(String token, Long usuarioId, String nome) {
        this(token, usuarioId, nome, null);
    }
}
