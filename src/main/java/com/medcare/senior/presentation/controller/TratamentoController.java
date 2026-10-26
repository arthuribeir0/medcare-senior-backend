package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.EditarTratamentoUseCase;
import com.medcare.senior.application.usecase.ListarTratamentosUseCase;
import com.medcare.senior.application.usecase.SalvarTratamentoUseCase;
import com.medcare.senior.application.usecase.TratamentoSalvo;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;

import com.medcare.senior.presentation.dto.TratamentoEdicaoRequestDTO;
import com.medcare.senior.presentation.dto.TratamentoRequestDTO;
import com.medcare.senior.presentation.dto.TratamentoResponseDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
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

    private static final String ANTECEDENCIA = "Se hoje é um dia do tratamento (dentro do período e num dos dias da "
            + "semana), nenhum horário pode cair do minuto atual até daqui a menos de 5 minutos (400 VALIDACAO). Horários "
            + "que já passaram hoje são aceitos: a primeira dose fica para o próximo dia válido, informado em "
            + "primeiraDose.";
    private static final String EXEMPLO_HORARIO_MUITO_PROXIMO = """
            {"timestamp": "2026-10-08T14:30:00", "status": 400, "erro": "Bad Request", "codigo": "VALIDACAO",
             "mensagem": "Não é possível cadastrar um horário que seja agora ou daqui a menos de 5 minutos. Escolha um \
            horário com pelo menos 5 minutos de diferença do horário atual.",
             "campos": [{"campo": "horarios", "mensagem": "Não é possível cadastrar um horário que seja agora ou daqui \
            a menos de 5 minutos. Escolha um horário com pelo menos 5 minutos de diferença do horário atual."}]}""";

    private final SalvarTratamentoUseCase salvarTratamentoUseCase;
    private final ListarTratamentosUseCase listarTratamentosUseCase;
    private final EditarTratamentoUseCase editarTratamentoUseCase;

    @PostMapping("/api/medicamentos/{medicamentoId}/tratamentos")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Cadastra um tratamento para o medicamento e gera as doses do horizonte (padrão de 30 dias)",
            description = "Ao menos um horário, sem horários repetidos; ao menos um dia da semana; "
                    + "dataFim (opcional) igual ou posterior a dataInicio.\n\n" + ANTECEDENCIA)
    @ApiResponse(responseCode = "201", description = "Tratamento cadastrado; primeiraDose diz quando é a primeira dose")
    @ApiResponse(responseCode = "400", description = "Campos inválidos ou horário de hoje a menos de 5 minutos "
            + "(VALIDACAO); horários, dias da semana ou período inválidos (TRATAMENTO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(name = "HORARIO_MUITO_PROXIMO", summary = "Horário agora ou a menos de 5 minutos",
                            value = EXEMPLO_HORARIO_MUITO_PROXIMO)))
    @ApiResponse(responseCode = "404", description = "Medicamento inexistente, removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<TratamentoResponseDTO> cadastrar(@Parameter(description = "ID do medicamento")
                                                           @PathVariable Long medicamentoId,
                                                           @Valid @RequestBody TratamentoRequestDTO request,
                                                           @Parameter(hidden = true)
                                                           @AuthenticationPrincipal AuthenticatedUser usuario) {
        TratamentoSalvo salvo = salvarTratamentoUseCase.executar(medicamentoId, request.dataInicio(), request.dataFim(),
                request.horarios(), request.diasSemana(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(TratamentoResponseDTO.from(salvo));
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
                    + "Mesmas regras do cadastro.\n\n" + ANTECEDENCIA + " Na edição, só os horários adicionados ou "
                    + "alterados são verificados; os que já existiam não bloqueiam.")
    @ApiResponse(responseCode = "200", description = "Tratamento atualizado; primeiraDose diz quando é a primeira dose")
    @ApiResponse(responseCode = "400", description = "Campos inválidos ou horário novo de hoje a menos de 5 minutos "
            + "(VALIDACAO); horários, dias da semana ou período inválidos (TRATAMENTO_INVALIDO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class),
                    examples = @ExampleObject(name = "HORARIO_MUITO_PROXIMO", summary = "Horário agora ou a menos de 5 minutos",
                            value = EXEMPLO_HORARIO_MUITO_PROXIMO)))
    @ApiResponse(responseCode = "404", description = "Tratamento inexistente, removido ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<TratamentoResponseDTO> editar(@Parameter(description = "ID do tratamento") @PathVariable Long id,
                                                        @Valid @RequestBody TratamentoEdicaoRequestDTO request,
                                                        @Parameter(hidden = true)
                                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        TratamentoSalvo salvo = editarTratamentoUseCase.executar(id, request.dataFim(), request.horarios(),
                request.diasSemana(), usuario.cuidadorId());
        return ResponseEntity.ok(TratamentoResponseDTO.from(salvo));
    }
}
