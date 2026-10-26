package com.medcare.senior.presentation.controller;

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
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
@Tag(name = "Cuidadores", description = "Cadastro, exclusão de conta e token de dispositivo de cuidadores")
public class CuidadorController {

    private final CadastrarCuidadorUseCase cadastrarCuidadorUseCase;
    private final ExcluirContaUseCase excluirContaUseCase;
    private final RegistrarTokenDispositivoUseCase registrarTokenDispositivoUseCase;
    private final BuscarCuidadorUseCase buscarCuidadorUseCase;
    private final RemoverTokenDispositivoUseCase removerTokenDispositivoUseCase;

    @PostMapping
    @SecurityRequirements
    @Operation(summary = "Cadastra um cuidador", description = "Endpoint público. O e-mail deve ser único (RN-001), "
            + "a senha forte (RN-002) e o termo de consentimento LGPD aceito (consentimentoLgpd=true).")
    @ApiResponse(responseCode = "201", description = "Cuidador cadastrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos, senha fraca ou consentimento LGPD não aceito",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<CuidadorResponseDTO> cadastrar(@Valid @RequestBody CuidadorRequestDTO request) {
        Cuidador cuidador = cadastrarCuidadorUseCase.executar(
                request.nome(), request.email(), request.senha(), request.telefone(), request.consentimentoLgpd());
        return ResponseEntity.status(HttpStatus.CREATED).body(CuidadorResponseDTO.from(cuidador));
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do cuidador autenticado", description = "Somente CUIDADOR.")
    @ApiResponse(responseCode = "200", description = "Dados do cuidador")
    @ApiResponse(responseCode = "401", description = "Token ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)",
            content = @Content)
    @ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
    public ResponseEntity<CuidadorResponseDTO> buscarMe(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(CuidadorResponseDTO.from(buscarCuidadorUseCase.executar(usuario.cuidadorId())));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Exclui a conta do cuidador autenticado (LGPD)",
            description = "Remove de forma irreversível o cuidador e todos os seus pacientes, medicamentos, tratamentos "
                    + "e códigos de vínculo. Somente CUIDADOR. Ver docs/lgpd.md.")
    @ApiResponse(responseCode = "204", description = "Conta excluída")
    @ApiResponse(responseCode = "401", description = "Token ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)",
            content = @Content)
    @ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
    public ResponseEntity<Void> excluirConta(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        excluirContaUseCase.executar(usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/me/fcm-token")
    @Operation(summary = "Registra o token de dispositivo do cuidador autenticado",
            description = "Grava (ou troca) o token FCM do aparelho do cuidador, usado para avisos push de dose não "
                    + "confirmada. Um aparelho pertence a uma conta por vez: o mesmo token é retirado de qualquer "
                    + "outra conta. O token é dado pessoal e é apagado na exclusão de conta. Somente CUIDADOR.")
    @ApiResponse(responseCode = "204", description = "Token registrado")
    @ApiResponse(responseCode = "400", description = "Token ausente, vazio ou maior que 512 caracteres",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "401",
            description = "Token de acesso ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)", content = @Content)
    @ApiResponse(responseCode = "403", description = "Token de acesso sem o papel CUIDADOR", content = @Content)
    public ResponseEntity<Void> registrarTokenDispositivo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario,
            @Valid @RequestBody TokenDispositivoRequestDTO request) {
        registrarTokenDispositivoUseCase.executar(usuario.cuidadorId(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/fcm-token")
    @Operation(summary = "Apaga o token de dispositivo do cuidador autenticado",
            description = "Usado no logout: o aparelho deixa de receber avisos push desta conta. Idempotente: responde "
                    + "204 mesmo se não houver token gravado. Somente CUIDADOR.")
    @ApiResponse(responseCode = "204", description = "Token apagado (ou já não havia token)")
    @ApiResponse(responseCode = "401",
            description = "Token de acesso ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)", content = @Content)
    @ApiResponse(responseCode = "403", description = "Token de acesso sem o papel CUIDADOR", content = @Content)
    public ResponseEntity<Void> removerTokenDispositivo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        removerTokenDispositivoUseCase.executar(usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }
}
