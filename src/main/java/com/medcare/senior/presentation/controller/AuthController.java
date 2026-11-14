package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.AutenticarCuidadorUseCase;
import com.medcare.senior.application.usecase.RedefinirSenhaUseCase;
import com.medcare.senior.application.usecase.SolicitarRedefinicaoDeSenhaUseCase;
import com.medcare.senior.domain.exception.CodigoPorEmailInvalidoException;
import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.infrastructure.security.RateLimiter;
import com.medcare.senior.presentation.dto.AuthResponseDTO;
import com.medcare.senior.presentation.dto.EsqueciSenhaRequestDTO;
import com.medcare.senior.presentation.dto.LoginRequestDTO;
import com.medcare.senior.presentation.dto.RedefinirSenhaRequestDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import com.medcare.senior.presentation.exception.MuitasTentativasException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação")
public class AuthController {

    // Contadores do RateLimiter separados do pareamento (mesmo componente, chaves diferentes)
    static final String CHAVE_ESQUECI = "esqueci-senha:";
    static final String CHAVE_REDEFINIR = "redefinir-senha:";

    private final AutenticarCuidadorUseCase autenticarCuidadorUseCase;
    private final SolicitarRedefinicaoDeSenhaUseCase solicitarRedefinicaoDeSenhaUseCase;
    private final RedefinirSenhaUseCase redefinirSenhaUseCase;
    private final RateLimiter rateLimiter;

    @PostMapping("/login")
    @PerfilExigido(Perfil.PUBLICO)
    @Operation(summary = "Autentica o cuidador", description = "Troca e-mail e senha por um JWT com papel CUIDADOR. "
            + "emailVerificado=false: o app pede o código enviado ao e-mail antes de deixar cadastrar pacientes.")
    @ApiResponse(responseCode = "200", description = "Autenticado; devolve o token, o id e o nome do cuidador e se o "
            + "e-mail já foi confirmado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO) ou corpo ausente ou malformado (CORPO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos, com a mesma resposta para e-mail inexistente e "
            + "senha errada (CREDENCIAIS_INVALIDAS)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(AuthResponseDTO.bearer(autenticarCuidadorUseCase.executar(request.email(), request.senha())));
    }

    @PostMapping("/esqueci-senha")
    @PerfilExigido(Perfil.PUBLICO)
    @Operation(summary = "Envia o código para criar uma senha nova",
            description = "Se o e-mail tem conta, envia a ele um código de 6 dígitos (vale 15 minutos). Responde 204 "
                    + "sempre, com ou sem conta, para não revelar quais e-mails estão cadastrados. Até 5 pedidos por IP "
                    + "a cada 15 minutos; outro código para a mesma conta só depois de 1 minuto (o anterior continua valendo).")
    @ApiResponse(responseCode = "204", description = "Pedido aceito (o código chega se o e-mail tiver conta)")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "429", description = "Muitos pedidos deste IP (LIMITE_TENTATIVAS, header Retry-After)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> esqueciSenha(@Valid @RequestBody EsqueciSenhaRequestDTO request,
                                             HttpServletRequest httpRequest) {
        String chave = CHAVE_ESQUECI + httpRequest.getRemoteAddr();
        barrarSeBloqueado(chave);
        // Todo pedido conta: limita o envio de e-mails a partir de um mesmo IP
        rateLimiter.registrarFalha(chave);
        solicitarRedefinicaoDeSenhaUseCase.executar(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/redefinir-senha")
    @PerfilExigido(Perfil.PUBLICO)
    @Operation(summary = "Cria uma senha nova com o código do e-mail",
            description = "Confere o código enviado por /esqueci-senha e grava a senha nova (mínimo de 8 caracteres, "
                    + "com maiúscula, minúscula e número). Também confirma o e-mail. 5 códigos errados invalidam o "
                    + "código; 5 falhas por IP em 15 minutos bloqueiam o IP até o fim da janela.")
    @ApiResponse(responseCode = "204", description = "Senha trocada")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO), senha fraca (SENHA_FRACA) ou código "
            + "errado, vencido, bloqueado ou de e-mail sem conta (CODIGO_EMAIL_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "429", description = "Muitas falhas deste IP (LIMITE_TENTATIVAS, header Retry-After)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> redefinirSenha(@Valid @RequestBody RedefinirSenhaRequestDTO request,
                                               HttpServletRequest httpRequest) {
        String chave = CHAVE_REDEFINIR + httpRequest.getRemoteAddr();
        barrarSeBloqueado(chave);
        try {
            redefinirSenhaUseCase.executar(request.email(), request.codigo(), request.novaSenha());
        } catch (CodigoPorEmailInvalidoException e) {
            rateLimiter.registrarFalha(chave);
            throw e;
        }
        return ResponseEntity.noContent().build();
    }

    private void barrarSeBloqueado(String chave) {
        long segundos = rateLimiter.segundosAteLiberar(chave);
        if (segundos > 0) {
            throw new MuitasTentativasException(segundos);
        }
    }
}
