/*
 * Porta (interface) do hash de senha: o domínio e os casos de uso pedem "gerar hash" e "conferir" sem saber que a
 * implementação usa BCrypt.
 */
package com.medcare.senior.domain.autenticacao;

public interface PasswordHasherPort {

    String hash(String senha);

    boolean matches(String senha, String hash);
}
