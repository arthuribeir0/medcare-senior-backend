package com.medcare.senior.domain.email;

import java.util.regex.Pattern;

/** RN-002: mínimo de 8 caracteres, com ao menos uma maiúscula, uma minúscula e um número (cadastro e redefinição). */
public final class PoliticaDeSenha {

    private static final Pattern SENHA_FORTE = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$");

    private PoliticaDeSenha() {
    }

    public static boolean forte(String senha) {
        return senha != null && SENHA_FORTE.matcher(senha).matches();
    }
}
