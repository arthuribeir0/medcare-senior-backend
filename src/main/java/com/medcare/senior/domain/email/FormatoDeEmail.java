package com.medcare.senior.domain.email;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Formato aceito para o e-mail do cuidador, o mesmo do app (src/lib/schemas.ts): algo@dominio.ext, sem espaços, com ao
 * menos um ponto no domínio e nenhuma parte vazia. Mais estrito que o {@code @Email} do Bean Validation, que aceita
 * "a@b".
 */
public final class FormatoDeEmail {

    public static final String REGEX = "^[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+$";
    private static final Pattern PADRAO = Pattern.compile(REGEX);

    private FormatoDeEmail() {
    }

    public static boolean valido(String email) {
        return email != null && PADRAO.matcher(email.trim()).matches();
    }

    /** Sem espaços nas pontas e em minúsculas: a forma gravada e comparada (RN-001). */
    public static String normalizar(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** "ana@Gmail.com" -> "gmail.com". */
    public static String dominio(String email) {
        String normalizado = normalizar(email);
        return normalizado.substring(normalizado.lastIndexOf('@') + 1);
    }
}
