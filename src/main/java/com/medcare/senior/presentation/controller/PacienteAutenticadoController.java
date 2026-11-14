package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.BuscarPacienteAutenticadoUseCase;
import com.medcare.senior.application.usecase.ListarDosesDoDiaUseCase;
import com.medcare.senior.application.usecase.RegistrarTokenDoPacienteUseCase;
import com.medcare.senior.application.usecase.RemoverTokenDoPacienteUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.presentation.dto.DoseResponseDTO;
import com.medcare.senior.presentation.dto.PacienteAutenticadoResponseDTO;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
public class PacienteAutenticadoController {

    private final BuscarPacienteAutenticadoUseCase buscarPacienteAutenticadoUseCase;
    private final ListarDosesDoDiaUseCase listarDosesDoDiaUseCase;
    private final RegistrarTokenDoPacienteUseCase registrarTokenDoPacienteUseCase;
    private final RemoverTokenDoPacienteUseCase removerTokenDoPacienteUseCase;

    @GetMapping
    @Tag(name = "Pacientes")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "Dados do paciente autenticado e o nome de quem cuida dele")
    @ApiResponse(responseCode = "200", description = "Dados do paciente")
    public ResponseEntity<PacienteAutenticadoResponseDTO> buscar(@Parameter(hidden = true)
                                                              @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(PacienteAutenticadoResponseDTO.from(buscarPacienteAutenticadoUseCase.executar(usuario.pacienteId())));
    }

    @GetMapping("/doses")
    @Tag(name = "Pacientes")
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

    @PutMapping("/fcm-token")
    @Tag(name = "Push/Token")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "Registra o token de dispositivo do paciente autenticado",
            description = "Grava (ou troca) o token FCM do aparelho do paciente, usado pelo servidor para reforçar o "
                    + "lembrete de dose (o lembrete principal é agendado no próprio aparelho). O paciente vem sempre do "
                    + "token JWT; o corpo só traz o token do aparelho. Um aparelho pertence a um paciente por vez: o "
                    + "mesmo token é retirado de qualquer outro paciente. O token é dado pessoal e é apagado ao sair da "
                    + "conta, na desconexão do aparelho, na desvinculação do paciente e na exclusão da conta.")
    @ApiResponse(responseCode = "204", description = "Token registrado")
    @ApiResponse(responseCode = "400", description = "Token ausente, vazio ou maior que 512 caracteres (TOKEN_DISPOSITIVO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> registrarTokenDispositivo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario,
            @Valid @RequestBody TokenDispositivoRequestDTO request) {
        registrarTokenDoPacienteUseCase.executar(usuario.pacienteId(), request.token());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/fcm-token")
    @Tag(name = "Push/Token")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "Apaga o token de dispositivo do paciente autenticado",
            description = "Usado quando o app sai da conta do paciente: o aparelho deixa de receber o lembrete do "
                    + "servidor. Idempotente: responde 204 mesmo se não houver token gravado.")
    @ApiResponse(responseCode = "204", description = "Token apagado (ou já não havia token)")
    public ResponseEntity<Void> removerTokenDispositivo(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        removerTokenDoPacienteUseCase.executar(usuario.pacienteId());
        return ResponseEntity.noContent().build();
    }
}
