package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.BuscarCuidadorUseCase;
import com.medcare.senior.application.usecase.CadastrarCuidadorUseCase;
import com.medcare.senior.application.usecase.ConfirmarEmailUseCase;
import com.medcare.senior.application.usecase.ReenviarCodigoDeVerificacaoUseCase;
import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.application.usecase.RegistrarTokenDispositivoUseCase;
import com.medcare.senior.application.usecase.RemoverTokenDispositivoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.presentation.dto.CodigoEmailRequestDTO;
import com.medcare.senior.presentation.dto.CuidadorRequestDTO;
import com.medcare.senior.presentation.dto.CuidadorResponseDTO;
import com.medcare.senior.presentation.dto.TokenDispositivoRequestDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cuidadores")
@RequiredArgsConstructor
public class CuidadorController {

    private final CadastrarCuidadorUseCase cadastrarCuidadorUseCase;
    private final ExcluirContaUseCase excluirContaUseCase;
    private final RegistrarTokenDispositivoUseCase registrarTokenDispositivoUseCase;
    private final BuscarCuidadorUseCase buscarCuidadorUseCase;
    private final RemoverTokenDispositivoUseCase removerTokenDispositivoUseCase;
    private final ConfirmarEmailUseCase confirmarEmailUseCase;
    private final ReenviarCodigoDeVerificacaoUseCase reenviarCodigoDeVerificacaoUseCase;

    @PostMapping
    @Tag(name = "Cuidador")
    @PerfilExigido(Perfil.PUBLICO)
    @Operation(summary = "Cadastra um cuidador", description = "O e-mail deve ser único e de um domínio que existe, a "
            + "senha forte (no mínimo 8 caracteres, com maiúscula, minúscula e número) e o termo de consentimento LGPD "
            + "aceito (consentimentoLgpd=true). A conta nasce com o e-mail não confirmado (emailVerificado=false) e um "
            + "código de 6 dígitos é enviado a ele; sem confirmar, o cuidador não cadastra pacientes.")
    @ApiResponse(responseCode = "201", description = "Cuidador cadastrado; código de confirmação enviado ao e-mail")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO), e-mail de domínio que não existe "
            + "(EMAIL_INVALIDO), senha fraca (SENHA_FRACA) ou termo LGPD não aceito (CONSENTIMENTO_OBRIGATORIO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado (EMAIL_DUPLICADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<CuidadorResponseDTO> cadastrar(@Valid @RequestBody CuidadorRequestDTO request) {
        Cuidador cuidador = cadastrarCuidadorUseCase.executar(
                request.nome(), request.email(), request.senha(), request.telefone(), request.consentimentoLgpd());
        return ResponseEntity.status(HttpStatus.CREATED).body(CuidadorResponseDTO.from(cuidador));
    }

    @GetMapping("/me")
    @Tag(name = "Cuidador")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Dados do cuidador autenticado", description = "Nome, e-mail e telefone da conta do token.")
    @ApiResponse(responseCode = "200", description = "Dados do cuidador")
    public ResponseEntity<CuidadorResponseDTO> buscarMe(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(CuidadorResponseDTO.from(buscarCuidadorUseCase.executar(usuario.cuidadorId())));
    }

    @PostMapping("/me/verificacao-email/confirmar")
    @Tag(name = "Cuidador")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Confirma o e-mail do cuidador autenticado",
            description = "Com o código de 6 dígitos enviado ao e-mail no cadastro (ou por /reenviar). Vale 15 minutos; "
                    + "5 códigos errados invalidam o código. Já confirmado: responde 200 sem conferir nada.")
    @ApiResponse(responseCode = "200", description = "E-mail confirmado; devolve o cuidador")
    @ApiResponse(responseCode = "400", description = "Código errado (com as tentativas que restam), vencido ou bloqueado "
            + "(CODIGO_EMAIL_INVALIDO), ou campo ausente (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<CuidadorResponseDTO> confirmarEmail(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario,
            @Valid @RequestBody CodigoEmailRequestDTO request) {
        return ResponseEntity.ok(CuidadorResponseDTO.from(confirmarEmailUseCase.executar(usuario.cuidadorId(), request.codigo())));
    }

    @PostMapping("/me/verificacao-email/reenviar")
    @Tag(name = "Cuidador")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Envia outro código de confirmação ao e-mail",
            description = "Substitui o código anterior. Só depois de 1 minuto do anterior. E-mail já confirmado: nada é "
                    + "enviado.")
    @ApiResponse(responseCode = "204", description = "Código enviado (ou e-mail já confirmado)")
    @ApiResponse(responseCode = "429", description = "Menos de 1 minuto depois do anterior (LIMITE_TENTATIVAS, header "
            + "Retry-After com os segundos que faltam)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> reenviarCodigo(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        reenviarCodigoDeVerificacaoUseCase.executar(usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me")
    @Tag(name = "Cuidador")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Exclui a conta do cuidador autenticado (LGPD)",
            description = "Remove de forma irreversível o cuidador e todos os seus pacientes, medicamentos, tratamentos "
                    + "e códigos de vínculo. O histórico de doses fica só de forma anônima. Ver docs/lgpd.md.")
    @ApiResponse(responseCode = "204", description = "Conta excluída")
    public ResponseEntity<Void> excluirConta(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        excluirContaUseCase.executar(usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/fcm-token")
    @Tag(name = "Push/Token")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Registra o token de dispositivo do cuidador autenticado",
            description = "Grava (ou troca) o token FCM do aparelho do cuidador, usado para avisos push de dose não "
                    + "confirmada. Um aparelho pertence a uma conta por vez: o mesmo token é retirado de qualquer "
                    + "outra conta. O token é dado pessoal e é apagado na exclusão de conta.")
    @ApiResponse(responseCode = "204", description = "Token registrado")
    @ApiResponse(responseCode = "400", description = "Token ausente, vazio ou maior que 512 caracteres (TOKEN_DISPOSITIVO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> registrarTokenDispositivo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario,
            @Valid @RequestBody TokenDispositivoRequestDTO request) {
        registrarTokenDispositivoUseCase.executar(usuario.cuidadorId(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/fcm-token")
    @Tag(name = "Push/Token")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Apaga o token de dispositivo do cuidador autenticado",
            description = "Usado no logout: o aparelho deixa de receber avisos push desta conta. Idempotente: responde "
                    + "204 mesmo se não houver token gravado.")
    @ApiResponse(responseCode = "204", description = "Token apagado (ou já não havia token)")
    public ResponseEntity<Void> removerTokenDispositivo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        removerTokenDispositivoUseCase.executar(usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }
}
