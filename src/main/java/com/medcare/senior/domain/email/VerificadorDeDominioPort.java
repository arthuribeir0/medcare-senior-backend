package com.medcare.senior.domain.email;

/** Confere se o domínio de um e-mail existe e recebe e-mail (registro MX, ou A/AAAA na falta dele). */
public interface VerificadorDeDominioPort {

    /**
     * @return {@code false} só quando o domínio com certeza não existe; sem como saber (DNS fora do ar, demora),
     * {@code true}: na dúvida, o cadastro não é bloqueado
     */
    boolean dominioRecebeEmail(String dominio);
}
