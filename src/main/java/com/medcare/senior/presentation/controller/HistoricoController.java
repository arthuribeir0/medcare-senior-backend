package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.ConsultarHistoricoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.presentation.dto.HistoricoResponseDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/historico")
@RequiredArgsConstructor
@Tag(name = "Histórico")
public class HistoricoController {

    private final ConsultarHistoricoUseCase consultarHistoricoUseCase;

    @GetMapping
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Histórico de doses, paginado, da mais recente para a mais antiga",
            description = "Sempre restrito aos pacientes do cuidador. Filtros opcionais e combináveis. 'de' não "
                    + "pode ser depois de 'ate'; sem 'ate', vale hoje (fuso da aplicação). Período sem registros "
                    + "devolve página vazia (não é erro). Inclui doses de remédio removido (histórico preservado).")
    @ApiResponse(responseCode = "200", description = "Página do histórico (pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "Data ou status em formato inválido, 'de' depois de 'ate', "
            + "página negativa ou tamanho fora de 1 a 100 (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<HistoricoResponseDTO> listar(
            @Parameter(description = "Só as doses deste paciente") @RequestParam(required = false) Long pacienteId,
            @Parameter(description = "Só as doses deste medicamento") @RequestParam(required = false)
            Long medicamentoId,
            @Parameter(description = "Primeiro dia (AAAA-MM-DD), inclusivo", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Último dia (AAAA-MM-DD), inclusivo; padrão: hoje", example = "2026-10-18")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @Parameter(description = "Só este status gravado") @RequestParam(required = false) StatusDose status,
            @Parameter(description = "Página, a partir de 0", example = "0", schema = @Schema(minimum = "0")) @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página (padrão 20, máximo 100)", example = "20",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(HistoricoResponseDTO.from(consultarHistoricoUseCase.executar(usuario.cuidadorId(),
                pacienteId, medicamentoId, de, ate, status, page, size)));
    }
}
