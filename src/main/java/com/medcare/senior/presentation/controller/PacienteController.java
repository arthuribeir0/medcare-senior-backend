package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.BuscarPacienteUseCase;
import com.medcare.senior.application.usecase.CadastrarPacienteUseCase;
import com.medcare.senior.application.usecase.GerarCodigoVinculoUseCase;
import com.medcare.senior.application.usecase.ListarDosesDoDiaUseCase;
import com.medcare.senior.application.usecase.ListarPacientesUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.presentation.dto.CodigoVinculoResponseDTO;
import com.medcare.senior.presentation.dto.DoseResponseDTO;
import com.medcare.senior.presentation.dto.PacienteRequestDTO;
import com.medcare.senior.presentation.dto.PacienteResponseDTO;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Acesso restrito a CUIDADOR no SecurityConfig. O ID do cuidador vem sempre do JWT
 * (principal preenchido pelo JwtAuthenticationFilter), nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/api/pacientes")
@RequiredArgsConstructor
@Tag(name = "Pacientes")
public class PacienteController {

    private final CadastrarPacienteUseCase cadastrarPacienteUseCase;
    private final GerarCodigoVinculoUseCase gerarCodigoVinculoUseCase;
    private final ListarPacientesUseCase listarPacientesUseCase;
    private final BuscarPacienteUseCase buscarPacienteUseCase;
    private final ListarDosesDoDiaUseCase listarDosesDoDiaUseCase;

    @PostMapping
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Cadastra um paciente vinculado ao cuidador autenticado",
            description = "Não pode haver dois pacientes com o mesmo nome e data de nascimento para o mesmo cuidador.")
    @ApiResponse(responseCode = "201", description = "Paciente cadastrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "Mesmo nome (sem diferenciar maiúsculas) e data de nascimento de outro paciente "
            + "do cuidador (PACIENTE_DUPLICADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<PacienteResponseDTO> cadastrar(@Valid @RequestBody PacienteRequestDTO request,
                                                      @Parameter(hidden = true)
                                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        Paciente paciente = cadastrarPacienteUseCase.executar(
                request.nome(), request.dataNascimento(), request.telefone(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(PacienteResponseDTO.from(paciente));
    }

    @GetMapping
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Lista os pacientes do cuidador autenticado",
            description = "Ordem alfabética de nome, sem diferenciar maiúsculas de minúsculas.")
    @ApiResponse(responseCode = "200", description = "Lista de pacientes (pode ser vazia)")
    public ResponseEntity<List<PacienteResponseDTO>> listar(@Parameter(hidden = true)
                                                         @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<PacienteResponseDTO> pacientes = listarPacientesUseCase.executar(usuario.cuidadorId()).stream()
                .map(PacienteResponseDTO::from)
                .toList();
        return ResponseEntity.ok(pacientes);
    }

    @GetMapping("/{id}")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Busca um paciente do cuidador autenticado")
    @ApiResponse(responseCode = "200", description = "Paciente encontrado")
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<PacienteResponseDTO> buscar(@Parameter(description = "ID do paciente", example = "1") @PathVariable Long id,
                                                   @Parameter(hidden = true)
                                                   @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(PacienteResponseDTO.from(buscarPacienteUseCase.executar(id, usuario.cuidadorId())));
    }

    @GetMapping("/{id}/doses")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Doses de um paciente do cuidador em um dia",
            description = "Ordenadas por horário. Janela, statusExibicao e confirmabilidade são calculados no servidor.")
    @ApiResponse(responseCode = "200", description = "Doses do dia (pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "Data em formato inválido (codigo VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<List<DoseResponseDTO>> listarDoses(
            @Parameter(description = "ID do paciente") @PathVariable Long id,
            @Parameter(description = "Dia (AAAA-MM-DD); padrão: hoje no fuso da aplicação", example = "2026-10-08")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<DoseResponseDTO> doses = listarDosesDoDiaUseCase.executarComoCuidador(id, usuario.cuidadorId(), data).stream()
                .map(DoseResponseDTO::from)
                .toList();
        return ResponseEntity.ok(doses);
    }

    @PostMapping("/{id}/gerar-codigo")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Gera um código de vínculo de 6 dígitos para o paciente",
            description = "O código vale 24 horas e só pode ser usado uma vez.")
    @ApiResponse(responseCode = "201", description = "Código gerado")
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<CodigoVinculoResponseDTO> gerarCodigo(@Parameter(description = "ID do paciente") @PathVariable Long id,
                                                                @Parameter(hidden = true)
                                                                @AuthenticationPrincipal AuthenticatedUser usuario) {
        CodigoVinculo codigo = gerarCodigoVinculoUseCase.executar(id, usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CodigoVinculoResponseDTO.from(codigo));
    }
}
