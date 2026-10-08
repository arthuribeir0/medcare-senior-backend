package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.CadastrarCuidadorUseCase;
import com.medcare.senior.application.usecase.ExcluirContaUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.presentation.dto.CuidadorRequestDTO;
import com.medcare.senior.presentation.dto.CuidadorResponseDTO;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cuidadores")
@RequiredArgsConstructor
@Tag(name = "Cuidadores", description = "Cadastro e exclusão de conta de cuidadores")
public class CuidadorController {

    private final CadastrarCuidadorUseCase cadastrarCuidadorUseCase;
    private final ExcluirContaUseCase excluirContaUseCase;

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

    @DeleteMapping("/me")
    @Operation(summary = "Exclui a conta do cuidador autenticado (LGPD)",
            description = "Remove de forma irreversível o cuidador e todos os seus idosos, medicamentos, tratamentos "
                    + "e códigos de vínculo. Somente CUIDADOR. Ver docs/lgpd.md.")
    @ApiResponse(responseCode = "204", description = "Conta excluída")
    @ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
    @ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
    @ApiResponse(responseCode = "404", description = "Conta já excluída",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> excluirConta(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        excluirContaUseCase.executar(usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }
}
