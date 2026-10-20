package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.GerarRelatorioDiarioUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.presentation.dto.RelatorioDiarioResponseDTO;
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
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
@Tag(name = "Relatórios", description = "Relatório diário de uso dos medicamentos")
@ApiResponse(responseCode = "401", description = "Token ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)",
        content = @Content)
@ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
public class RelatorioController {

    private final GerarRelatorioDiarioUseCase gerarRelatorioDiarioUseCase;

    @GetMapping("/diario")
    @Operation(summary = "Relatório diário de um idoso do cuidador (UC-008)",
            description = "Doses do dia (com janela, statusExibicao e confirmabilidade calculados no servidor) e os "
                    + "totais pelo status gravado.")
    @ApiResponse(responseCode = "200", description = "Relatório do dia (a lista de doses pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "idosoId ausente ou data em formato inválido",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<RelatorioDiarioResponseDTO> diario(
            @Parameter(description = "ID do idoso", required = true) @RequestParam Long idosoId,
            @Parameter(description = "Dia (AAAA-MM-DD); padrão: hoje no fuso da aplicação", example = "2026-10-18")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(RelatorioDiarioResponseDTO.from(
                gerarRelatorioDiarioUseCase.executar(idosoId, usuario.cuidadorId(), data)));
    }
}
