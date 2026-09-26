package com.medcare.senior.domain.autenticacao;

public interface PasswordHasherPort {

    String hash(String senha);

    boolean matches(String senha, String hash);
}
