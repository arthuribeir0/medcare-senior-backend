package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.BuscarCuidadorUseCase;
import com.medcare.senior.application.usecase.CadastrarCuidadorUseCase;
import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.application.usecase.RegistrarTokenDispositivoUseCase;
import com.medcare.senior.application.usecase.RemoverTokenDispositivoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.Cuidador;
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

    @PostMapping
    @Tag(name = "Cuidador")
    @PerfilExigido(Perfil.PUBLICO)
    @Operation(summary = "Cadastra um cuidador", description = "O e-mail deve ser único, a senha forte (no mínimo 8 caracteres, com maiúscula, "
            + "minúscula e número) e o termo de consentimento LGPD aceito (consentimentoLgpd=true).")
    @ApiResponse(responseCode = "201", description = "Cuidador cadastrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO), senha fraca (SENHA_FRACA) ou termo LGPD não "
            + "aceito (CONSENTIMENTO_OBRIGATORIO)",
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
