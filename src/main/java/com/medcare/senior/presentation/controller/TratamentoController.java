package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.EditarTratamentoUseCase;
import com.medcare.senior.application.usecase.ListarTratamentosUseCase;
import com.medcare.senior.application.usecase.SalvarTratamentoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.presentation.dto.TratamentoEdicaoRequestDTO;
import com.medcare.senior.presentation.dto.TratamentoRequestDTO;
import com.medcare.senior.presentation.dto.TratamentoResponseDTO;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Acesso restrito a CUIDADOR no SecurityConfig (regras de /api/medicamentos/** e /api/tratamentos/**). O ID do
 * cuidador vem sempre do JWT, e os use cases verificam a posse pelo caminho tratamento -> medicamento -> paciente.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Medicamentos e tratamentos")
public class TratamentoController {

    private final SalvarTratamentoUseCase salvarTratamentoUseCase;
    private final ListarTratamentosUseCase listarTratamentosUseCase;
    private final EditarTratamentoUseCase editarTratamentoUseCase;

    @PostMapping("/api/medicamentos/{medicamentoId}/tratamentos")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Cadastra um tratamento para o medicamento e gera as doses do horizonte (padrão de 30 dias)",
            description = "Ao menos um horário, sem horários repetidos; ao menos um dia da semana; "
                    + "dataFim (opcional) igual ou posterior a dataInicio.")
    @ApiResponse(responseCode = "201", description = "Tratamento cadastrado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO) ou horários, dias da semana ou período "
            + "inválidos (TRATAMENTO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Medicamento inexistente, removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<TratamentoResponseDTO> cadastrar(@Parameter(description = "ID do medicamento")
                                                           @PathVariable Long medicamentoId,
                                                           @Valid @RequestBody TratamentoRequestDTO request,
                                                           @Parameter(hidden = true)
                                                           @AuthenticationPrincipal AuthenticatedUser usuario) {
        Tratamento tratamento = salvarTratamentoUseCase.executar(medicamentoId, request.dataInicio(), request.dataFim(),
                request.horarios(), request.diasSemana(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(TratamentoResponseDTO.from(tratamento));
    }

    @GetMapping("/api/medicamentos/{medicamentoId}/tratamentos")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Lista os tratamentos do medicamento, do mais antigo para o mais recente")
    @ApiResponse(responseCode = "200", description = "Lista de tratamentos (pode ser vazia)")
    @ApiResponse(responseCode = "404", description = "Medicamento inexistente, removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<List<TratamentoResponseDTO>> listar(@Parameter(description = "ID do medicamento")
                                                              @PathVariable Long medicamentoId,
                                                              @Parameter(hidden = true)
                                                              @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<TratamentoResponseDTO> tratamentos = listarTratamentosUseCase.executar(medicamentoId, usuario.cuidadorId())
                .stream()
                .map(TratamentoResponseDTO::from)
                .toList();
        return ResponseEntity.ok(tratamentos);
    }

    @PutMapping("/api/tratamentos/{id}")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Edita horários, dias da semana e data de término de um tratamento",
            description = "A data de início não muda. As doses PENDENTES futuras são apagadas e geradas de novo "
                    + "conforme a nova configuração; doses já registradas e doses passadas não são alteradas. "
                    + "Mesmas regras do cadastro.")
    @ApiResponse(responseCode = "200", description = "Tratamento atualizado")
    @ApiResponse(responseCode = "400", description = "Campos inválidos (VALIDACAO) ou horários, dias da semana ou período "
            + "inválidos (TRATAMENTO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Tratamento inexistente, removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<TratamentoResponseDTO> editar(@Parameter(description = "ID do tratamento") @PathVariable Long id,
                                                        @Valid @RequestBody TratamentoEdicaoRequestDTO request,
                                                        @Parameter(hidden = true)
                                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        Tratamento tratamento = editarTratamentoUseCase.executar(id, request.dataFim(), request.horarios(),
                request.diasSemana(), usuario.cuidadorId());
        return ResponseEntity.ok(TratamentoResponseDTO.from(tratamento));
    }
}
