package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.GerarRelatorioDiarioUseCase;
import com.medcare.senior.application.usecase.GerarRelatorioPdfUseCase;
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
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@Tag(name = "Relatórios", description = "Relatório diário de uso dos medicamentos e histórico de adesão em PDF")
@ApiResponse(responseCode = "401", description = "Token ausente ou inválido, ou de conta já excluída (SESSAO_INVALIDA)",
        content = @Content)
@ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
public class RelatorioController {

    private final GerarRelatorioDiarioUseCase gerarRelatorioDiarioUseCase;
    private final GerarRelatorioPdfUseCase gerarRelatorioPdfUseCase;

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

    @GetMapping(value = "/pdf", produces = {MediaType.APPLICATION_PDF_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Histórico de adesão em PDF de um idoso do cuidador (UC-012, HU10)",
            description = "RN-012: nome do idoso, período, medicamentos avaliados, cada dose (data, hora programada, "
                    + "status e hora da confirmação), taxa de adesão por medicamento e do período ((no horário + com "
                    + "atraso) / (total - pendentes), 1 casa). RN-011: 'de' obrigatório e não posterior a 'ate'; sem "
                    + "'ate', vale hoje (fuso da aplicação). Período sem doses não gera arquivo (422).")
    @ApiResponse(responseCode = "200", description = "Arquivo PDF (Content-Disposition: attachment)",
            content = @Content(mediaType = MediaType.APPLICATION_PDF_VALUE))
    @ApiResponse(responseCode = "400", description = "idosoId ou 'de' ausente, data em formato inválido ou 'de' depois "
            + "de 'ate' (VALIDACAO)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "422", description = "Nenhuma dose no período (PERIODO_SEM_REGISTROS)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<byte[]> pdf(
            @Parameter(description = "ID do idoso", required = true) @RequestParam Long idosoId,
            @Parameter(description = "Primeiro dia (AAAA-MM-DD), inclusivo", example = "2026-10-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @Parameter(description = "Último dia (AAAA-MM-DD), inclusivo; padrão: hoje", example = "2026-10-18")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        GerarRelatorioPdfUseCase.Resultado resultado = gerarRelatorioPdfUseCase.executar(usuario.cuidadorId(), idosoId,
                de, ate);
        // Nome do arquivo sem dado pessoal: só o ID do idoso e o período
        String arquivo = "historico-adesao-" + idosoId + "-" + resultado.relatorio().de() + "-a-"
                + resultado.relatorio().ate() + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(arquivo).build().toString())
                .cacheControl(CacheControl.noStore())
                .body(resultado.pdf());
    }
}
