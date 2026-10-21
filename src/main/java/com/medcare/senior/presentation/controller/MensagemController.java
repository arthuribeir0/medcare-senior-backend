package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.ContarMensagensNaoLidasUseCase;
import com.medcare.senior.application.usecase.EnviarMensagemUseCase;
import com.medcare.senior.application.usecase.ListarConversasUseCase;
import com.medcare.senior.application.usecase.ListarMensagensUseCase;
import com.medcare.senior.application.usecase.MarcarMensagensComoLidasUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.presentation.dto.ConversaResponseDTO;
import com.medcare.senior.presentation.dto.MensagemRequestDTO;
import com.medcare.senior.presentation.dto.MensagemResponseDTO;
import com.medcare.senior.presentation.dto.MensagensLidasResponseDTO;
import com.medcare.senior.presentation.dto.MensagensResponseDTO;
import com.medcare.senior.presentation.dto.NaoLidasResponseDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mensagens entre o cuidador e o idoso (UC-010). CUIDADOR e IDOSO no SecurityConfig; quem pode ver qual conversa é
 * decidido no AcessoAConversaService. O conteúdo das mensagens nunca vai para o log.
 */
@RestController
@RequestMapping("/api/mensagens")
@RequiredArgsConstructor
@Tag(name = "Mensagens", description = "Conversa entre o cuidador e cada idoso (CUIDADOR e IDOSO)")
@ApiResponse(responseCode = "401", description = "Token ausente ou inválido; ou usuário do token não existe mais "
        + "(codigo SESSAO_INVALIDA)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
public class MensagemController {

    private final EnviarMensagemUseCase enviarMensagemUseCase;
    private final ListarConversasUseCase listarConversasUseCase;
    private final ListarMensagensUseCase listarMensagensUseCase;
    private final MarcarMensagensComoLidasUseCase marcarMensagensComoLidasUseCase;
    private final ContarMensagensNaoLidasUseCase contarMensagensNaoLidasUseCase;

    @PostMapping
    @Operation(summary = "Envia uma mensagem",
            description = "Cuidador informa o idosoId (um idoso seu); o idoso escreve na própria conversa. Conteúdo de "
                    + "1 a 500 caracteres, sem só espaços (UC-010, fluxo 3a).")
    @ApiResponse(responseCode = "201", description = "Mensagem enviada")
    @ApiResponse(responseCode = "400", description = "Conteúdo vazio, só espaços ou maior que 500; ou cuidador sem "
            + "idosoId (VALIDACAO com o campo)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Idoso informando outro idoso (ACESSO_NEGADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MensagemResponseDTO> enviar(@RequestBody MensagemRequestDTO corpo,
                                                      @Parameter(hidden = true)
                                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(MensagemResponseDTO.from(
                enviarMensagemUseCase.executar(usuario, corpo.idosoId(), corpo.conteudo())));
    }

    @GetMapping("/conversas")
    @Operation(summary = "Lista de conversas",
            description = "Cuidador: uma por idoso seu (inclusive sem mensagens), a de mensagem mais recente primeiro. "
                    + "Idoso: a sua única conversa, no mesmo formato. naoLidas = mensagens do outro lado ainda não lidas.")
    @ApiResponse(responseCode = "200", description = "Conversas (lista vazia para cuidador sem idosos)")
    public ResponseEntity<List<ConversaResponseDTO>> conversas(@Parameter(hidden = true)
                                                               @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(listarConversasUseCase.executar(usuario).stream().map(ConversaResponseDTO::from).toList());
    }

    @GetMapping
    @Operation(summary = "Mensagens de uma conversa, paginadas, da mais recente para a mais antiga",
            description = "Cuidador informa o idosoId; o idoso vê a própria conversa (idosoId opcional).")
    @ApiResponse(responseCode = "200", description = "Página da conversa (pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "Cuidador sem idosoId, página negativa ou tamanho fora de 1 a 100 "
            + "(VALIDACAO)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Idoso informando outro idoso (ACESSO_NEGADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MensagensResponseDTO> listar(
            @Parameter(description = "Idoso da conversa (obrigatório para o cuidador)") @RequestParam(required = false)
            Long idosoId,
            @Parameter(description = "Página, a partir de 0", example = "0") @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página (padrão 20, máximo 100)", example = "20")
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(MensagensResponseDTO.from(listarMensagensUseCase.executar(usuario, idosoId, page, size)));
    }

    @PutMapping("/lidas")
    @Operation(summary = "Marca como lidas as mensagens recebidas na conversa",
            description = "Só as mensagens do outro lado mudam; idempotente (de novo, marca 0). Cuidador informa o "
                    + "idosoId; o idoso marca a própria conversa.")
    @ApiResponse(responseCode = "200", description = "Quantas foram marcadas agora")
    @ApiResponse(responseCode = "400", description = "Cuidador sem idosoId (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Idoso informando outro idoso (ACESSO_NEGADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Idoso inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MensagensLidasResponseDTO> marcarComoLidas(
            @Parameter(description = "Idoso da conversa (obrigatório para o cuidador)") @RequestParam(required = false)
            Long idosoId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(new MensagensLidasResponseDTO(marcarMensagensComoLidasUseCase.executar(usuario, idosoId)));
    }

    @GetMapping("/nao-lidas")
    @Operation(summary = "Total de mensagens recebidas e ainda não lidas",
            description = "Cuidador: somando as conversas de todos os seus idosos. Idoso: as do cuidador.")
    @ApiResponse(responseCode = "200", description = "Contador")
    public ResponseEntity<NaoLidasResponseDTO> naoLidas(@Parameter(hidden = true)
                                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(new NaoLidasResponseDTO(contarMensagensNaoLidasUseCase.executar(usuario)));
    }
}
