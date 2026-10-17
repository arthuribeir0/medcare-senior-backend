package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.BuscarIdosoUseCase;
import com.medcare.senior.application.usecase.CadastrarIdosoUseCase;
import com.medcare.senior.application.usecase.GerarCodigoVinculoUseCase;
import com.medcare.senior.application.usecase.ListarIdososUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.presentation.dto.CodigoVinculoResponseDTO;
import com.medcare.senior.presentation.dto.IdosoRequestDTO;
import com.medcare.senior.presentation.dto.IdosoResponseDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Acesso restrito a CUIDADOR no SecurityConfig. O ID do cuidador vem sempre do JWT
 * (principal preenchido pelo JwtAuthenticationFilter), nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/api/idosos")
@RequiredArgsConstructor
@Tag(name = "Idosos", description = "Cadastro de idosos e geração de código de vínculo (somente CUIDADOR)")
@ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
@ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
public class IdosoController {

    private final CadastrarIdosoUseCase cadastrarIdosoUseCase;
    private final GerarCodigoVinculoUseCase gerarCodigoVinculoUseCase;
    private final ListarIdososUseCase listarIdososUseCase;
    private final BuscarIdosoUseCase buscarIdosoUseCase;

    @PostMapping
    @Operation(summary = "Cadastra um idoso vinculado ao cuidador autenticado",
            description = "Não pode haver dois idosos com o mesmo nome e data de nascimento para o mesmo cuidador (RN-003).")
    @ApiResponse(responseCode = "201", description = "Idoso cadastrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "Idoso duplicado (RN-003)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<IdosoResponseDTO> cadastrar(@Valid @RequestBody IdosoRequestDTO request,
                                                      @Parameter(hidden = true)
                                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        Idoso idoso = cadastrarIdosoUseCase.executar(
                request.nome(), request.dataNascimento(), request.telefone(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(IdosoResponseDTO.from(idoso));
    }

    @GetMapping
    @Operation(summary = "Lista os idosos do cuidador autenticado",
            description = "Ordem alfabética de nome, sem diferenciar maiúsculas de minúsculas.")
    @ApiResponse(responseCode = "200", description = "Lista de idosos (pode ser vazia)")
    public ResponseEntity<List<IdosoResponseDTO>> listar(@Parameter(hidden = true)
                                                         @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<IdosoResponseDTO> idosos = listarIdososUseCase.executar(usuario.cuidadorId()).stream()
                .map(IdosoResponseDTO::from)
                .toList();
        return ResponseEntity.ok(idosos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um idoso do cuidador autenticado")
    @ApiResponse(responseCode = "200", description = "Idoso encontrado")
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<IdosoResponseDTO> buscar(@Parameter(description = "ID do idoso") @PathVariable Long id,
                                                   @Parameter(hidden = true)
                                                   @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(IdosoResponseDTO.from(buscarIdosoUseCase.executar(id, usuario.cuidadorId())));
    }

    @PostMapping("/{id}/gerar-codigo")
    @Operation(summary = "Gera um código de vínculo de 6 dígitos para o idoso",
            description = "O código vale 24 horas e só pode ser usado uma vez.")
    @ApiResponse(responseCode = "201", description = "Código gerado")
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<CodigoVinculoResponseDTO> gerarCodigo(@Parameter(description = "ID do idoso") @PathVariable Long id,
                                                                @Parameter(hidden = true)
                                                                @AuthenticationPrincipal AuthenticatedUser usuario) {
        CodigoVinculo codigo = gerarCodigoVinculoUseCase.executar(id, usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CodigoVinculoResponseDTO.from(codigo));
    }
}
