package com.medcare.senior.domain.email;

/** Para que serve um código enviado ao e-mail do cuidador. Cada finalidade tem o seu código, independente. */
public enum FinalidadeDoCodigo {
    /** Confirmar que o e-mail do cadastro é mesmo do cuidador. */
    VERIFICACAO,
    /** "Esqueci minha senha": quem tem acesso ao e-mail define uma senha nova. */
    REDEFINICAO_SENHA
}
