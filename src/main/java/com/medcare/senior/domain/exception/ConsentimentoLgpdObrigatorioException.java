package com.medcare.senior.domain.exception;

/**
 * LGPD: o cadastro do cuidador só é aceito com o aceite explícito do termo de consentimento.
 */
public class ConsentimentoLgpdObrigatorioException extends RuntimeException {

    public ConsentimentoLgpdObrigatorioException() {
        super("É necessário aceitar o termo de consentimento de dados (LGPD) para concluir o cadastro");
    }
}
