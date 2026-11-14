package com.medcare.senior.infrastructure.email;

import com.medcare.senior.domain.email.EnviadorDeEmailPort;
import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Sem envio de e-mail (app.email.enabled=false, o padrão no desenvolvimento e nos testes): o código não sai do servidor.
 * Com app.email.log-do-codigo=true, ele aparece no log, para testar o app sem um servidor SMTP (só em ambiente de
 * teste ou apresentação: quem lê o log consegue confirmar qualquer conta).
 */
@Component
@ConditionalOnProperty(name = "app.email.enabled", havingValue = "false", matchIfMissing = true)
public class LogEnviadorDeEmailAdapter implements EnviadorDeEmailPort {

    private static final Logger log = LoggerFactory.getLogger(LogEnviadorDeEmailAdapter.class);

    private final boolean logDoCodigo;

    public LogEnviadorDeEmailAdapter(@Value("${app.email.log-do-codigo:false}") boolean logDoCodigo) {
        this.logDoCodigo = logDoCodigo;
        if (logDoCodigo) {
            log.warn("Envio de e-mail desligado e app.email.log-do-codigo=true: os códigos de confirmação e de troca de "
                    + "senha vão para o log. Use só para testes.");
        }
    }

    @Override
    public boolean enviarCodigo(String destinatario, String nome, FinalidadeDoCodigo finalidade, String codigo) {
        String dominio = SmtpEnviadorDeEmailAdapter.dominio(destinatario);
        if (logDoCodigo) {
            log.warn("[E-MAIL DESLIGADO] Código de {} para {}: {}", finalidade, destinatario, codigo);
        } else {
            log.info("Envio de e-mail desligado: código de {} para um e-mail de {} não enviado", finalidade, dominio);
        }
        return false;
    }
}
