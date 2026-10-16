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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleEmailJaCadastrado(EmailJaCadastradoException ex) {
        return construirResposta(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(SenhaFracaException.class)
    public ResponseEntity<ErroResponse> handleSenhaFraca(SenhaFracaException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(ConsentimentoLgpdObrigatorioException.class)
    public ResponseEntity<ErroResponse> handleConsentimentoLgpdObrigatorio(ConsentimentoLgpdObrigatorioException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(TokenDispositivoInvalidoException.class)
    public ResponseEntity<ErroResponse> handleTokenDispositivoInvalido(TokenDispositivoInvalidoException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResponse> handleCredenciaisInvalidas(CredenciaisInvalidasException ex) {
        return construirResposta(HttpStatus.UNAUTHORIZED, ex.getMessage(), List.of());
    }

    @ExceptionHandler(IdosoJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleIdosoJaCadastrado(IdosoJaCadastradoException ex) {
        return construirResposta(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> handleRecursoNaoEncontrado(RecursoNaoEncontradoException ex) {
        return construirResposta(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResponse> handleAcessoNegado(AcessoNegadoException ex) {
        return construirResposta(HttpStatus.FORBIDDEN, ex.getMessage(), List.of());
    }

    @ExceptionHandler(CodigoVinculoInvalidoException.class)
    public ResponseEntity<ErroResponse> handleCodigoVinculoInvalido(CodigoVinculoInvalidoException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MuitasTentativasException.class)
    public ResponseEntity<ErroResponse> handleMuitasTentativas(MuitasTentativasException ex) {
        ResponseEntity<ErroResponse> resposta = construirResposta(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), List.of());
        return ResponseEntity.status(resposta.getStatusCode())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getSegundosAteLiberar()))
                .body(resposta.getBody());
    }

    @ExceptionHandler(DadosObrigatoriosMedicamentoException.class)
    public ResponseEntity<ErroResponse> handleDadosObrigatoriosMedicamento(DadosObrigatoriosMedicamentoException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MedicamentoComTratamentoAtivoException.class)
    public ResponseEntity<ErroResponse> handleMedicamentoComTratamentoAtivo(MedicamentoComTratamentoAtivoException ex) {
        return construirResposta(HttpStatus.CONFLICT, ex.getMessage(), List.of());
    }

    // RN-007: regras de validação do tratamento
    @ExceptionHandler({HorariosObrigatoriosException.class, HorariosDuplicadosException.class,
            DiasSemanaObrigatoriosException.class, PeriodoTratamentoInvalidoException.class})
    public ResponseEntity<ErroResponse> handleTratamentoInvalido(RuntimeException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    // Corpo ausente, JSON malformado, codificação inválida ou valor em formato errado (ex.: data "31/12/2026",
    // dia "SEGUNDA"). A mensagem é genérica de propósito: os detalhes do parser expõem classes internas.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> handleCorpoIlegivel(HttpMessageNotReadableException ex) {
        return construirResposta(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido: verifique se é um JSON válido em UTF-8 e se os campos estão no formato esperado",
                List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidacao(MethodArgumentNotValidException ex) {
        List<ErroResponse.CampoInvalido> campos = ex.getBindingResult().getFieldErrors().stream()
                .map(erro -> new ErroResponse.CampoInvalido(erro.getField(), erro.getDefaultMessage()))
                .toList();
        return construirResposta(HttpStatus.BAD_REQUEST, "Um ou mais campos são inválidos", campos);
    }

    private ResponseEntity<ErroResponse> construirResposta(HttpStatus status, String mensagem,
                                                           List<ErroResponse.CampoInvalido> campos) {
        ErroResponse corpo = new ErroResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensagem,
                campos
        );
        return ResponseEntity.status(status).body(corpo);
    }
}
