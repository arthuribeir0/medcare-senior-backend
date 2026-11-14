package com.medcare.senior.infrastructure.email;

import com.medcare.senior.domain.email.FinalidadeDoCodigo;

/** Assunto e texto do e-mail com o código, em linguagem simples. */
final class TextoDoEmail {

    private TextoDoEmail() {
    }

    static String assunto(FinalidadeDoCodigo finalidade, String codigo) {
        return switch (finalidade) {
            case VERIFICACAO -> codigo + " é o seu código do MedCare Sênior";
            case REDEFINICAO_SENHA -> codigo + " é o código para trocar a sua senha do MedCare Sênior";
        };
    }

    static String corpo(String nome, FinalidadeDoCodigo finalidade, String codigo) {
        String primeiroNome = nome == null || nome.isBlank() ? "" : " " + nome.trim().split("\\s+")[0];
        String motivo = switch (finalidade) {
            case VERIFICACAO -> "Para confirmar o seu e-mail no MedCare Sênior, digite este código no aplicativo:";
            case REDEFINICAO_SENHA -> "Para criar uma senha nova no MedCare Sênior, digite este código no aplicativo:";
        };
        String seNaoFoiVoce = switch (finalidade) {
            case VERIFICACAO -> "Se você não criou uma conta no MedCare Sênior, ignore este e-mail.";
            case REDEFINICAO_SENHA -> "Se você não pediu para trocar a senha, ignore este e-mail: a sua senha continua a mesma.";
        };
        return "Olá" + primeiroNome + ",\n\n"
                + motivo + "\n\n"
                + "    " + codigo + "\n\n"
                + "O código vale por 15 minutos. Não compartilhe com ninguém.\n\n"
                + seNaoFoiVoce + "\n\n"
                + "MedCare Sênior";
    }
}
