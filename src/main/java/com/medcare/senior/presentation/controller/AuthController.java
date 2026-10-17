package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.AutenticarCuidadorUseCase;
import com.medcare.senior.presentation.dto.AuthResponseDTO;
import com.medcare.senior.presentation.dto.LoginRequestDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Autenticação", description = "Login do cuidador")
public class AuthController {

    private final AutenticarCuidadorUseCase autenticarCuidadorUseCase;

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Autentica o cuidador", description = "Troca e-mail e senha por um JWT com papel CUIDADOR.")
    @ApiResponse(responseCode = "200", description = "Autenticado; devolve o token e o id e o nome do cuidador")
    @ApiResponse(responseCode = "400", description = "Campos inválidos",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "401", description = "E-mail ou senha inválidos",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(AuthResponseDTO.bearer(autenticarCuidadorUseCase.executar(request.email(), request.senha())));
    }
}
