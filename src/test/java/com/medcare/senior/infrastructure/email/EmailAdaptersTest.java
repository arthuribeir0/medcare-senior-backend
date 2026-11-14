package com.medcare.senior.infrastructure.email;

import com.medcare.senior.domain.email.FinalidadeDoCodigo;
import com.medcare.senior.domain.email.FormatoDeEmail;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentCaptor.forClass;

/** Envio por SMTP, texto do e-mail, envio desligado, formato do e-mail e a chave de desligar o DNS (sem rede). */
class EmailAdaptersTest {

    @Test
    void smtpMandaOCodigoNoAssuntoENoTextoDoRemetenteConfigurado() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpEnviadorDeEmailAdapter adapter = new SmtpEnviadorDeEmailAdapter(mailSender, "MedCare <conta@gmail.com>");

        assertThat(adapter.enviarCodigo("ana@email.com", "Ana Souza", FinalidadeDoCodigo.VERIFICACAO, "482915")).isTrue();

        var captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage mensagem = captor.getValue();
        assertThat(mensagem.getFrom()).isEqualTo("MedCare <conta@gmail.com>");
        assertThat(mensagem.getTo()).containsExactly("ana@email.com");
        assertThat(mensagem.getSubject()).isEqualTo("482915 é o seu código do MedCare Sênior");
        assertThat(mensagem.getText()).startsWith("Olá Ana,").contains("482915").contains("15 minutos");
    }

    @Test
    void falhaDoSmtpNaoLancaENaoDizQueEnviou() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));
        SmtpEnviadorDeEmailAdapter adapter = new SmtpEnviadorDeEmailAdapter(mailSender, "conta@gmail.com");

        assertThat(adapter.enviarCodigo("ana@email.com", "Ana", FinalidadeDoCodigo.REDEFINICAO_SENHA, "1")).isFalse();
    }

    @Test
    void smtpSemRemetenteNaoSobe() {
        assertThatThrownBy(() -> new SmtpEnviadorDeEmailAdapter(mock(JavaMailSender.class), " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_REMETENTE");
    }

    @Test
    void textoDaTrocaDeSenhaExplicaOQueFazerSeNaoFoiVoce() {
        assertThat(TextoDoEmail.assunto(FinalidadeDoCodigo.REDEFINICAO_SENHA, "123456")).startsWith("123456 ");
        assertThat(TextoDoEmail.corpo(null, FinalidadeDoCodigo.REDEFINICAO_SENHA, "123456"))
                .startsWith("Olá,").contains("a sua senha continua a mesma");
    }

    @Test
    void envioDesligadoNuncaDizQueEnviou() {
        assertThat(new LogEnviadorDeEmailAdapter(false).enviarCodigo("a@b.com", "A", FinalidadeDoCodigo.VERIFICACAO, "1")).isFalse();
        assertThat(new LogEnviadorDeEmailAdapter(true).enviarCodigo("a@b.com", "A", FinalidadeDoCodigo.VERIFICACAO, "1")).isFalse();
    }

    @Test
    void verificacaoDeDominioDesligadaAceitaTudo() {
        assertThat(new DnsVerificadorDeDominioAdapter(false).dominioRecebeEmail("nao-existe.invalid")).isTrue();
        assertThat(new DnsVerificadorDeDominioAdapter(true).dominioRecebeEmail(" ")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@email.com", " Ana.Souza+med@Exemplo.com.br ", "a@b.co"})
    void formatoAceito(String email) {
        assertThat(FormatoDeEmail.valido(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana@b", "ana", "@email.com", "ana@.com", "ana@email.", "ana @email.com", "ana@@email.com", ""})
    void formatoRecusado(String email) {
        assertThat(FormatoDeEmail.valido(email)).isFalse();
    }

    @Test
    void normalizaEDevolveODominio() {
        assertThat(FormatoDeEmail.normalizar("  Ana@Email.COM ")).isEqualTo("ana@email.com");
        assertThat(FormatoDeEmail.dominio(" Ana@Gmail.com")).isEqualTo("gmail.com");
    }
}
