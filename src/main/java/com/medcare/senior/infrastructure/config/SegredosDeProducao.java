package com.medcare.senior.infrastructure.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Regras dos segredos obrigatórios em produção. As mensagens dizem QUAL variável está errada, nunca o valor.
 */
public final class SegredosDeProducao {

    /** Valor de desenvolvimento do application.yml (vale quando JWT_SECRET não é definido). */
    public static final String JWT_SECRET_PADRAO = "medcare-senior-dev-secret-key-trocar-em-producao-123456";
    /** Senha de desenvolvimento do application.yml (vale quando POSTGRES_PASSWORD não é definido). */
    public static final String SENHA_BANCO_PADRAO = "medcare_pass";
    public static final int JWT_SECRET_TAMANHO_MINIMO = 32;

    private SegredosDeProducao() {
    }

    /**
     * @return o que impede subir em produção (vazio = pode subir)
     */
    public static List<String> problemas(String jwtSecret, String senhaBanco) {
        List<String> problemas = new ArrayList<>();
        if (jwtSecret == null || jwtSecret.isBlank()) {
            problemas.add("JWT_SECRET ausente");
        } else if (jwtSecret.equals(JWT_SECRET_PADRAO)) {
            problemas.add("JWT_SECRET igual ao valor padrão de desenvolvimento do repositório");
        } else if (jwtSecret.length() < JWT_SECRET_TAMANHO_MINIMO) {
            problemas.add("JWT_SECRET com menos de " + JWT_SECRET_TAMANHO_MINIMO + " caracteres");
        }
        if (senhaBanco == null || senhaBanco.isBlank()) {
            problemas.add("POSTGRES_PASSWORD ausente");
        } else if (senhaBanco.equals(SENHA_BANCO_PADRAO)) {
            problemas.add("POSTGRES_PASSWORD igual à senha padrão de desenvolvimento do repositório");
        }
        return problemas;
    }
}
