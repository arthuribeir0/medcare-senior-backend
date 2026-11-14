package com.medcare.senior.infrastructure.email;

import com.medcare.senior.domain.email.VerificadorDeDominioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Confere no DNS se o domínio do e-mail existe: pega "hotmial.com.br" e "gmail.con", não prova que a caixa existe (isso
 * é o código de confirmação).
 *
 * <p>Uma consulta MX por domínio. Só a resposta "domínio não existe" (NXDOMAIN) barra o cadastro; qualquer outra coisa
 * (domínio sem MX, DNS fora do ar, demora acima de ~3 segundos) deixa passar: na dúvida, o cadastro não é bloqueado.
 * Consultar MX, A e AAAA de uma vez não serve: vários resolvedores respondem vazio a essa pergunta combinada.
 * Desligável com app.email.verificar-dominio=false (testes, ambiente sem DNS).
 */
@Component
public class DnsVerificadorDeDominioAdapter implements VerificadorDeDominioPort {

    private static final Logger log = LoggerFactory.getLogger(DnsVerificadorDeDominioAdapter.class);

    private final boolean ligado;

    public DnsVerificadorDeDominioAdapter(@Value("${app.email.verificar-dominio:true}") boolean ligado) {
        this.ligado = ligado;
    }

    @Override
    public boolean dominioRecebeEmail(String dominio) {
        if (!ligado || dominio == null || dominio.isBlank()) {
            return true;
        }
        Hashtable<String, String> ambiente = new Hashtable<>();
        ambiente.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        ambiente.put("com.sun.jndi.dns.timeout.initial", "1000");
        ambiente.put("com.sun.jndi.dns.timeout.retries", "2");
        try {
            InitialDirContext dns = new InitialDirContext(ambiente);
            try {
                dns.getAttributes(dominio, new String[]{"MX"});
                return true;
            } finally {
                dns.close();
            }
        } catch (NameNotFoundException e) {
            return false;
        } catch (NamingException | RuntimeException e) {
            log.info("Não foi possível conferir o domínio de um e-mail no DNS ({}); cadastro segue",
                    e.getClass().getSimpleName());
            return true;
        }
    }
}
