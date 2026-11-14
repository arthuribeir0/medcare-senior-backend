package com.medcare.senior.infrastructure.email;

import com.medcare.senior.domain.email.EnviadorDeEmailPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio do código por SMTP (app.email.enabled=true). Serve para qualquer provedor SMTP: Gmail com senha de app,
 * Brevo, Resend etc. (spring.mail.*, ver docs/email.md). Texto simples, sem links: o código é digitado no app.
 *
 * <p>Logs só com o domínio do destinatário, nunca o código nem o endereço completo.
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "true")
public class SmtpEnviadorDeEmailAdapter implements EnviadorDeEmailPort {

    private static final Logger log = LoggerFactory.getLogger(SmtpEnviadorDeEmailAdapter.class);

    private final JavaMailSender mailSender;
    private final String remetente;

    public SmtpEnviadorDeEmailAdapter(JavaMailSender mailSender, @Value("${app.email.remetente}") String remetente) {
        if (remetente == null || remetente.isBlank()) {
            throw new IllegalStateException("app.email.remetente (EMAIL_REMETENTE) é obrigatório com o envio ligado");
        }
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public boolean enviarCodigo(String destinatario, String nome, FinalidadeDoCodigo finalidade, String codigo) {
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(destinatario);
        mensagem.setSubject(TextoDoEmail.assunto(finalidade, codigo));
        mensagem.setText(TextoDoEmail.corpo(nome, finalidade, codigo));
        try {
            mailSender.send(mensagem);
            log.info("Código de {} enviado para um e-mail de {}", finalidade, dominio(destinatario));
            return true;
        } catch (MailException e) {
            log.warn("Falha ao enviar o código de {} para um e-mail de {}: {}", finalidade, dominio(destinatario),
                    e.getClass().getSimpleName());
            return false;
        }
    }

    static String dominio(String email) {
        int arroba = email.lastIndexOf('@');
        return arroba < 0 ? "?" : email.substring(arroba + 1);
    }
}
