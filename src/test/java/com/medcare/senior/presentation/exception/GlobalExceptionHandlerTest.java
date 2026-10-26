package com.medcare.senior.presentation.exception;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.DoseNaoConfirmavelException;
import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.domain.exception.ConsentimentoLgpdObrigatorioException;
import com.medcare.senior.domain.exception.CredenciaisInvalidasException;
import com.medcare.senior.domain.exception.EmailJaCadastradoException;
import com.medcare.senior.domain.exception.PacienteJaCadastradoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SenhaFracaException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.exception.TokenDispositivoInvalidoException;
import com.medcare.senior.domain.medicamento.DadosObrigatoriosMedicamentoException;
import com.medcare.senior.domain.medicamento.MedicamentoComTratamentoAtivoException;
import com.medcare.senior.domain.tratamento.HorariosObrigatoriosException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cada exceção conhecida vira um ErroResponse com o código estável esperado (tabela em CodigoErro).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapeiaAsExcecoesDeNegocioParaStatusECodigo() {
        assertErro(handler.handleEmailJaCadastrado(new EmailJaCadastradoException("a@b.com")), 409, "EMAIL_DUPLICADO");
        assertErro(handler.handleSenhaFraca(new SenhaFracaException()), 400, "SENHA_FRACA");
        assertErro(handler.handleConsentimentoLgpdObrigatorio(new ConsentimentoLgpdObrigatorioException()), 400,
                "CONSENTIMENTO_OBRIGATORIO");
        assertErro(handler.handleTokenDispositivoInvalido(new TokenDispositivoInvalidoException()), 400,
                "TOKEN_DISPOSITIVO_INVALIDO");
        assertErro(handler.handleCredenciaisInvalidas(new CredenciaisInvalidasException()), 401, "CREDENCIAIS_INVALIDAS");
        assertErro(handler.handlePacienteJaCadastrado(new PacienteJaCadastradoException()), 409, "PACIENTE_DUPLICADO");
        assertErro(handler.handleSessaoInvalida(new SessaoInvalidaException()), 401, "SESSAO_INVALIDA");
        assertErro(handler.handleRecursoNaoEncontrado(new RecursoNaoEncontradoException("Paciente não encontrado")), 404,
                "RECURSO_NAO_ENCONTRADO");
        assertErro(handler.handleAcessoNegado(new AcessoNegadoException()), 403, "ACESSO_NEGADO");
        assertErro(handler.handleCodigoVinculoInvalido(new CodigoVinculoInvalidoException()), 400, "CODIGO_INVALIDO");
        assertErro(handler.handleDadosObrigatoriosMedicamento(new DadosObrigatoriosMedicamentoException()), 400,
                "DADOS_OBRIGATORIOS_MEDICAMENTO");
        assertErro(handler.handleMedicamentoComTratamentoAtivo(new MedicamentoComTratamentoAtivoException()), 409,
                "TRATAMENTO_ATIVO");
        assertErro(handler.handleTratamentoInvalido(new HorariosObrigatoriosException()), 400, "TRATAMENTO_INVALIDO");
    }

    @Test
    void doseNaoConfirmavelTemUmCodigoPorMotivo() {
        assertErro(handler.handleDoseNaoConfirmavel(new DoseNaoConfirmavelException(Confirmabilidade.JA_REGISTRADA)), 409,
                "DOSE_JA_REGISTRADA");
        assertErro(handler.handleDoseNaoConfirmavel(new DoseNaoConfirmavelException(Confirmabilidade.MUITO_CEDO)), 409,
                "DOSE_MUITO_CEDO");
        assertErro(handler.handleDoseNaoConfirmavel(new DoseNaoConfirmavelException(Confirmabilidade.ENCERRADA)), 409,
                "DOSE_ENCERRADA");
    }

    @Test
    void limiteDeTentativasDevolveRetryAfter() {
        ResponseEntity<ErroResponse> resposta = handler.handleMuitasTentativas(new MuitasTentativasException(42));

        assertErro(resposta, 429, "LIMITE_TENTATIVAS");
        assertThat(resposta.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
    }

    @Test
    void corpoIlegivelTemMensagemGenerica() {
        HttpInputMessage entrada = new HttpInputMessage() {
            @Override
            public java.io.InputStream getBody() {
                return new ByteArrayInputStream(new byte[0]);
            }

            @Override
            public HttpHeaders getHeaders() {
                return new HttpHeaders();
            }
        };
        ResponseEntity<ErroResponse> resposta = handler.handleCorpoIlegivel(
                new HttpMessageNotReadableException("detalhe interno do parser", entrada));

        assertErro(resposta, 400, "CORPO_INVALIDO");
        assertThat(resposta.getBody().mensagem()).doesNotContain("parser");
    }

    @Test
    void erroInesperadoViraErroInternoSemVazarDetalhes() throws Exception {
        ResponseEntity<ErroResponse> resposta = handler.handleInesperado(
                new IllegalStateException("senha do banco: segredo"));

        assertErro(resposta, 500, "ERRO_INTERNO");
        assertThat(resposta.getBody().mensagem()).doesNotContain("segredo").doesNotContain("IllegalState");
    }

    @Test
    void erroDoSpringMvcMantemOStatus() throws Exception {
        ResponseEntity<ErroResponse> resposta = handler.handleInesperado(new HttpRequestMethodNotSupportedException("PATCH"));

        assertErro(resposta, 405, "METODO_NAO_PERMITIDO");
    }

    @Test
    void negacaoDoSpringSecurityNaoEhConvertidaEmErroInterno() {
        AccessDeniedException negado = new AccessDeniedException("negado");

        assertThatThrownBy(() -> handler.handleInesperado(negado)).isSameAs(negado);
    }

    private static void assertErro(ResponseEntity<ErroResponse> resposta, int status, String codigo) {
        assertThat(resposta.getStatusCode().value()).isEqualTo(status);
        assertThat(resposta.getBody()).isNotNull();
        assertThat(resposta.getBody().status()).isEqualTo(status);
        assertThat(resposta.getBody().codigo()).isEqualTo(codigo);
        assertThat(resposta.getBody().mensagem()).isNotBlank();
        assertThat(resposta.getBody().campos()).isNotNull();
    }
}
