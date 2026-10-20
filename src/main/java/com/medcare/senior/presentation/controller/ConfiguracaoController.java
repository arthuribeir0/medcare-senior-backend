package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.AtualizarToleranciaUseCase;
import com.medcare.senior.application.usecase.BuscarToleranciaUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.presentation.dto.ToleranciaRequestDTO;
import com.medcare.senior.presentation.dto.ToleranciaResponseDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/configuracoes")
@RequiredArgsConstructor
@Tag(name = "Configurações", description = "Tolerância do aviso de dose não confirmada, por cuidador")
@ApiResponse(responseCode = "401", description = "Token ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)",
        content = @Content)
@ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
public class ConfiguracaoController {

    private final BuscarToleranciaUseCase buscarToleranciaUseCase;
    private final AtualizarToleranciaUseCase atualizarToleranciaUseCase;

    @GetMapping("/tolerancia")
    @Operation(summary = "Tolerância efetiva do cuidador autenticado",
            description = "A própria, se configurada; senão, a padrão da aplicação (30 minutos).")
    @ApiResponse(responseCode = "200", description = "Tolerância em minutos")
    public ResponseEntity<ToleranciaResponseDTO> buscar(@Parameter(hidden = true)
                                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(new ToleranciaResponseDTO(buscarToleranciaUseCase.executar(usuario.cuidadorId())));
    }

    @PutMapping("/tolerancia")
    @Operation(summary = "Grava a tolerância do cuidador autenticado (RN-009)",
            description = "De 5 a 120 minutos, inclusive. Vale para os avisos ainda não enviados e para a janela das "
                    + "doses listadas a partir de agora; um aviso já enviado não é reenviado.")
    @ApiResponse(responseCode = "200", description = "Tolerância gravada")
    @ApiResponse(responseCode = "400", description = "Fora de 5 a 120 (VALIDACAO) ou corpo ausente/inválido "
            + "(CORPO_INVALIDO)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<ToleranciaResponseDTO> atualizar(@Parameter(hidden = true)
                                                           @AuthenticationPrincipal AuthenticatedUser usuario,
                                                           @Valid @RequestBody ToleranciaRequestDTO request) {
        int gravada = atualizarToleranciaUseCase.executar(usuario.cuidadorId(), request.toleranciaMinutos());
        return ResponseEntity.ok(new ToleranciaResponseDTO(gravada));
    }
}
