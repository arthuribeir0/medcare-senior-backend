package com.medcare.senior.presentation.exception;

import com.medcare.senior.domain.exception.AcessoNegadoException;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.domain.exception.ConsentimentoLgpdObrigatorioException;
import com.medcare.senior.domain.exception.CredenciaisInvalidasException;
import com.medcare.senior.domain.exception.EmailJaCadastradoException;
import com.medcare.senior.domain.exception.IdosoJaCadastradoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SenhaFracaException;
import com.medcare.senior.domain.exception.TokenDispositivoInvalidoException;
import com.medcare.senior.domain.medicamento.DadosObrigatoriosMedicamentoException;
import com.medcare.senior.domain.medicamento.MedicamentoComTratamentoAtivoException;
import com.medcare.senior.domain.tratamento.DiasSemanaObrigatoriosException;
import com.medcare.senior.domain.tratamento.HorariosDuplicadosException;
import com.medcare.senior.domain.tratamento.HorariosObrigatoriosException;
import com.medcare.senior.domain.tratamento.PeriodoTratamentoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Converte exceções em {@link ErroResponse} com um {@link CodigoErro} estável (tabela completa em CodigoErro).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        return construirResposta(HttpStatus.CONFLICT, CodigoErro.EMAIL_DUPLICADO, ex.getMessage(), List.of());
    }

    @ExceptionHandler(SenhaFracaException.class)
    public ResponseEntity<ErroResponse> handleSenhaFraca(SenhaFracaException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.SENHA_FRACA, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConsentimentoLgpdObrigatorioException.class)
    public ResponseEntity<ErroResponse> handleConsentimentoLgpdObrigatorio(ConsentimentoLgpdObrigatorioException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.CONSENTIMENTO_OBRIGATORIO, ex.getMessage(), List.of());
    }

    @ExceptionHandler(TokenDispositivoInvalidoException.class)
    public ResponseEntity<ErroResponse> handleTokenDispositivoInvalido(TokenDispositivoInvalidoException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.TOKEN_DISPOSITIVO_INVALIDO, ex.getMessage(), List.of());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponse> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        return construirResposta(HttpStatus.UNAUTHORIZED, CodigoErro.CREDENCIAIS_INVALIDAS, ex.getMessage(), List.of());
    }

    @ExceptionHandler(IdosoJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleIdosoJaCadastrado(IdosoJaCadastradoException ex) {
        return construirResposta(HttpStatus.CONFLICT, CodigoErro.IDOSO_DUPLICADO, ex.getMessage(), List.of());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return construirResposta(HttpStatus.NOT_FOUND, CodigoErro.RECURSO_NAO_ENCONTRADO, ex.getMessage(), List.of());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> handleAcessoNegado(AcessoNegadoException ex) {
        return construirResposta(HttpStatus.FORBIDDEN, CodigoErro.ACESSO_NEGADO, ex.getMessage(), List.of());
    }

    // Um código só para inexistente, expirado e já usado: não revela qual foi o motivo
    @ExceptionHandler(CodigoVinculoInvalidoException.class)
    public ResponseEntity<ErroResponse> handleCodigoVinculoInvalido(CodigoVinculoInvalidoException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.CODIGO_INVALIDO, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<ErroResponse> handleMuitasTentativas(MuitasTentativasException ex) {
        ResponseEntity<ErroResponse> resposta = construirResposta(HttpStatus.TOO_MANY_REQUESTS,
                CodigoErro.LIMITE_TENTATIVAS, ex.getMessage(), List.of());
        return ResponseEntity.status(resposta.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosAteLiberar()))
                .body(resposta.getBody());
    }

    @ExceptionHandler(DadosObrigatoriosMedicamentoException.class)
    public ResponseEntity<ErroResponse> handleDadosObrigatoriosMedicamento(DadosObrigatoriosMedicamentoException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.DADOS_OBRIGATORIOS_MEDICAMENTO, ex.getMessage(),
                List.of());
    }

    @ExceptionHandler(MedicamentoComTratamentoAtivoException.class)
    public ResponseEntity<ErroResponse> handleMedicamentoComTratamentoAtivo(MedicamentoComTratamentoAtivoException ex) {
        return construirResposta(HttpStatus.CONFLICT, CodigoErro.TRATAMENTO_ATIVO, ex.getMessage(), List.of());
    }

    // RN-007: regras de validação do tratamento
    @ExceptionHandler({HorariosObrigatoriosException.class, HorariosDuplicadosException.class,
            DiasSemanaObrigatoriosException.class, PeriodoTratamentoInvalidoException.class})
    public ResponseEntity<ErroResponse> handleTratamentoInvalido(RuntimeException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.TRATAMENTO_INVALIDO, ex.getMessage(), List.of());
    }

    // Corpo ausente, JSON malformado, codificação inválida ou valor em formato errado (ex.: data "31/12/2026",
    // dia "SEGUNDA"). A mensagem é genérica de propósito: os detalhes do parser expõem classes internas.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.CORPO_INVALIDO,
                "Corpo da requisição inválido: verifique se é um JSON válido em UTF-8 e se os campos estão no formato esperado",
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        List<ErroResponse.CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroResponse.CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .toList();
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.VALIDACAO, "Um ou mais campos são inválidos", campos);
    }

    // Parâmetro de rota ou de consulta com tipo/formato errado (ex.: id "abc", data "31-12-2026")
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> handleParametroInvalido(MethodArgumentTypeMismatchException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, CodigoErro.VALIDACAO, "Um ou mais campos são inválidos",
                List.of(new ErroResponse.CampoInvalido(ex.getName(), "Valor em formato inválido")));
    }

    /**
     * Último recurso. Erros do próprio Spring MVC que já têm status HTTP (405, 415, parâmetro ausente...) mantêm o
     * status; qualquer outra exceção vira 500 sem detalhes na resposta (o detalhe fica só no log).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleInesperado(Exception ex) throws Exception {
        // Autenticação/autorização são respondidas pelo Spring Security (401/403), não aqui
        if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
            throw ex;
        }
        if (ex instanceof ErrorResponse erroDoSpring && !erroDoSpring.getStatusCode().is5xxServerError()) {
            return respostaDoSpring(erroDoSpring.getStatusCode());
        }
        log.error("Erro inesperado ao processar a requisição", ex);
        return construirResposta(HttpStatus.INTERNAL_SERVER_ERROR, CodigoErro.ERRO_INTERNO,
                "Erro interno. Tente novamente mais tarde.", List.of());
    }

    private ResponseEntity<ErroResponse> respostaDoSpring(HttpStatusCode statusCode) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        return switch (status) {
            case NOT_FOUND -> construirResposta(status, CodigoErro.RECURSO_NAO_ENCONTRADO, "Recurso não encontrado", List.of());
            case METHOD_NOT_ALLOWED -> construirResposta(status, CodigoErro.METODO_NAO_PERMITIDO,
                    "Método HTTP não permitido para esta rota", List.of());
            default -> construirResposta(status, CodigoErro.REQUISICAO_INVALIDA, "Requisição inválida", List.of());
        };
    }

    private ResponseEntity<ErroResponse> construirResposta(HttpStatus status, CodigoErro codigo, String mensagem,
                                                           List<ErroResponse.CampoInvalido> campos) {
        ErroResponse corpo = new ErroResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                codigo.name(),
                mensagem,
                campos
        );
        return ResponseEntity.status(status).body(corpo);
    }
}
