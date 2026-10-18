package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.BuscarMedicamentoUseCase;
import com.medcare.senior.application.usecase.CadastrarMedicamentoUseCase;
import com.medcare.senior.application.usecase.EditarMedicamentoUseCase;
import com.medcare.senior.application.usecase.ListarMedicamentosUseCase;
import com.medcare.senior.application.usecase.RemoverMedicamentoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.presentation.dto.MedicamentoRequestDTO;
import com.medcare.senior.presentation.dto.MedicamentoResponseDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Acesso restrito a CUIDADOR no SecurityConfig. O ID do cuidador vem sempre do JWT, e os use cases
 * verificam que o idoso de cada medicamento pertence a esse cuidador.
 */
@RestController
@RequestMapping("/api/medicamentos")
@RequiredArgsConstructor
@Tag(name = "Medicamentos", description = "CRUD de medicamentos dos idosos do cuidador (somente CUIDADOR)")
@ApiResponse(responseCode = "401", description = "Token ausente ou inválido", content = @Content)
@ApiResponse(responseCode = "403", description = "Token sem o papel CUIDADOR", content = @Content)
public class MedicamentoController {

    private final CadastrarMedicamentoUseCase cadastrarMedicamentoUseCase;
    private final EditarMedicamentoUseCase editarMedicamentoUseCase;
    private final ListarMedicamentosUseCase listarMedicamentosUseCase;
    private final RemoverMedicamentoUseCase removerMedicamentoUseCase;
    private final BuscarMedicamentoUseCase buscarMedicamentoUseCase;

    @PostMapping
    @Operation(summary = "Cadastra um medicamento para um idoso do cuidador",
            description = "Nome, dosagem e tipo são obrigatórios (RN-005).")
    @ApiResponse(responseCode = "201", description = "Medicamento cadastrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MedicamentoResponseDTO> cadastrar(@Valid @RequestBody MedicamentoRequestDTO request,
                                                            @Parameter(hidden = true)
                                                            @AuthenticationPrincipal AuthenticatedUser usuario) {
        Medicamento medicamento = cadastrarMedicamentoUseCase.executar(request.toDomain(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(MedicamentoResponseDTO.from(medicamento));
    }

    @GetMapping
    @Operation(summary = "Lista os medicamentos de um idoso, em ordem alfabética")
    @ApiResponse(responseCode = "200", description = "Lista de medicamentos (pode ser vazia)")
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<List<MedicamentoResponseDTO>> listar(@Parameter(description = "ID do idoso") @RequestParam Long idosoId,
                                                               @Parameter(hidden = true)
                                                               @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<MedicamentoResponseDTO> medicamentos = listarMedicamentosUseCase.executar(idosoId, usuario.cuidadorId()).stream()
                .map(MedicamentoResponseDTO::from)
                .toList();
        return ResponseEntity.ok(medicamentos);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca um medicamento de um idoso do cuidador")
    @ApiResponse(responseCode = "200", description = "Medicamento encontrado")
    @ApiResponse(responseCode = "404", description = "Medicamento inexistente, removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MedicamentoResponseDTO> buscar(@Parameter(description = "ID do medicamento") @PathVariable Long id,
                                                         @Parameter(hidden = true)
                                                         @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(MedicamentoResponseDTO.from(buscarMedicamentoUseCase.executar(id, usuario.cuidadorId())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Substitui todos os dados de um medicamento",
            description = "O idoso pode ser trocado, desde que o novo idoso também pertença ao cuidador.")
    @ApiResponse(responseCode = "200", description = "Medicamento atualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Medicamento ou idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MedicamentoResponseDTO> editar(@Parameter(description = "ID do medicamento") @PathVariable Long id,
                                                         @Valid @RequestBody MedicamentoRequestDTO request,
                                                         @Parameter(hidden = true)
                                                         @AuthenticationPrincipal AuthenticatedUser usuario) {
        Medicamento medicamento = editarMedicamentoUseCase.executar(id, request.toDomain(), usuario.cuidadorId());
        return ResponseEntity.ok(MedicamentoResponseDTO.from(medicamento));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove um medicamento",
            description = "Remoção lógica: o medicamento e os tratamentos dele somem da API, mas o histórico de "
                    + "doses registradas é preservado (RN-008); as doses pendentes futuras são apagadas. "
                    + "Medicamento com tratamento ativo só é removido com forcarRemocao=true (RN-006).")
    @ApiResponse(responseCode = "204", description = "Medicamento removido")
    @ApiResponse(responseCode = "404", description = "Medicamento inexistente, já removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "Medicamento com tratamento ativo e sem forcarRemocao=true",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> remover(@Parameter(description = "ID do medicamento") @PathVariable Long id,
                                        @Parameter(description = "Confirma a remoção mesmo com tratamento ativo (RN-006)")
                                        @RequestParam(defaultValue = "false") boolean forcarRemocao,
                                        @Parameter(hidden = true)
                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        removerMedicamentoUseCase.executar(id, usuario.cuidadorId(), forcarRemocao);
        return ResponseEntity.noContent().build();
    }
}
