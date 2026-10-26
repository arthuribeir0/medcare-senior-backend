package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.BuscarPacienteAutenticadoUseCase;
import com.medcare.senior.application.usecase.ListarDosesDoDiaUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.presentation.dto.DoseResponseDTO;
import com.medcare.senior.presentation.dto.PacienteAutenticadoResponseDTO;
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
import java.util.List;

/**
 * Rotas do aparelho do paciente (papel PACIENTE no SecurityConfig, regras declaradas ANTES de /api/pacientes/**). O
 * mapeamento literal "/me" tem precedência sobre "/api/pacientes/{id}" do PacienteController.
 */
@RestController
@RequestMapping("/api/pacientes/me")
@RequiredArgsConstructor
@Tag(name = "Pacientes")
public class PacienteAutenticadoController {

    private final BuscarPacienteAutenticadoUseCase buscarPacienteAutenticadoUseCase;
    private final ListarDosesDoDiaUseCase listarDosesDoDiaUseCase;

    @GetMapping
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "Dados do paciente autenticado e o nome de quem cuida dele")
    @ApiResponse(responseCode = "200", description = "Dados do paciente")
    public ResponseEntity<PacienteAutenticadoResponseDTO> buscar(@Parameter(hidden = true)
                                                              @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(PacienteAutenticadoResponseDTO.from(buscarPacienteAutenticadoUseCase.executar(usuario.pacienteId())));
    }

    @GetMapping("/doses")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "Doses do paciente autenticado em um dia",
            description = "Ordenadas por horário. Janela, statusExibicao e confirmabilidade são calculados no servidor.")
    @ApiResponse(responseCode = "200", description = "Doses do dia (pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "Data em formato inválido (codigo VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<List<DoseResponseDTO>> listarDoses(
            @Parameter(description = "Dia (AAAA-MM-DD); padrão: hoje no fuso da aplicação", example = "2026-10-08")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<DoseResponseDTO> doses = listarDosesDoDiaUseCase.executarComoPaciente(usuario.pacienteId(), data).stream()
                .map(DoseResponseDTO::from)
                .toList();
        return ResponseEntity.ok(doses);
    }
}
