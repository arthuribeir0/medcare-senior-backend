package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
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
 * Mensagens entre o cuidador e o paciente (UC-010). CUIDADOR e PACIENTE no SecurityConfig; quem pode ver qual conversa é
 * decidido no AcessoAConversaService. O conteúdo das mensagens nunca vai para o log.
 */
@RestController
@RequestMapping("/api/mensagens")
@RequiredArgsConstructor
@Tag(name = "Mensagens")
public class MensagemController {

    private final EnviarMensagemUseCase enviarMensagemUseCase;
    private final ListarConversasUseCase listarConversasUseCase;
    private final ListarMensagensUseCase listarMensagensUseCase;
    private final MarcarMensagensComoLidasUseCase marcarMensagensComoLidasUseCase;
    private final ContarMensagensNaoLidasUseCase contarMensagensNaoLidasUseCase;

    @PostMapping
    @PerfilExigido(Perfil.CUIDADOR_OU_PACIENTE)
    @Operation(summary = "Envia uma mensagem",
            description = "Cuidador informa o pacienteId (um paciente seu); o paciente escreve na própria conversa. Conteúdo de "
                    + "1 a 500 caracteres, sem só espaços.")
    @ApiResponse(responseCode = "201", description = "Mensagem enviada")
    @ApiResponse(responseCode = "400", description = "Conteúdo vazio, só espaços ou maior que 500; ou cuidador sem "
            + "pacienteId (VALIDACAO com o campo)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Paciente informando outro paciente (ACESSO_NEGADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MensagemResponseDTO> enviar(@RequestBody MensagemRequestDTO corpo,
                                                      @Parameter(hidden = true)
                                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.status(HttpStatus.CREATED).body(MensagemResponseDTO.from(
                enviarMensagemUseCase.executar(usuario, corpo.pacienteId(), corpo.conteudo())));
    }

    @GetMapping("/conversas")
    @PerfilExigido(Perfil.CUIDADOR_OU_PACIENTE)
    @Operation(summary = "Lista de conversas",
            description = "Cuidador: uma por paciente seu (inclusive sem mensagens), a de mensagem mais recente primeiro. "
                    + "Paciente: a sua única conversa, no mesmo formato. naoLidas = mensagens do outro lado ainda não lidas.")
    @ApiResponse(responseCode = "200", description = "Conversas (lista vazia para cuidador sem pacientes)")
    public ResponseEntity<List<ConversaResponseDTO>> conversas(@Parameter(hidden = true)
                                                               @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(listarConversasUseCase.executar(usuario).stream().map(ConversaResponseDTO::from).toList());
    }

    @GetMapping
    @PerfilExigido(Perfil.CUIDADOR_OU_PACIENTE)
    @Operation(summary = "Mensagens de uma conversa, paginadas, da mais recente para a mais antiga",
            description = "Cuidador informa o pacienteId; o paciente vê a própria conversa (pacienteId opcional).")
    @ApiResponse(responseCode = "200", description = "Página da conversa (pode ser vazia)")
    @ApiResponse(responseCode = "400", description = "Cuidador sem pacienteId, página negativa ou tamanho fora de 1 a 100 "
            + "(VALIDACAO)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Paciente informando outro paciente (ACESSO_NEGADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MensagensResponseDTO> listar(
            @Parameter(description = "Paciente da conversa (obrigatório para o cuidador)") @RequestParam(required = false)
            Long pacienteId,
            @Parameter(description = "Página, a partir de 0", example = "0", schema = @Schema(minimum = "0")) @RequestParam(required = false) Integer page,
            @Parameter(description = "Tamanho da página (padrão 20, máximo 100)", example = "20",
                    schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20"))
            @RequestParam(required = false) Integer size,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(MensagensResponseDTO.from(listarMensagensUseCase.executar(usuario, pacienteId, page, size)));
    }

    @PutMapping("/lidas")
    @PerfilExigido(Perfil.CUIDADOR_OU_PACIENTE)
    @Operation(summary = "Marca como lidas as mensagens recebidas na conversa",
            description = "Só as mensagens do outro lado mudam; idempotente (de novo, marca 0). Cuidador informa o "
                    + "pacienteId; o paciente marca a própria conversa.")
    @ApiResponse(responseCode = "200", description = "Quantas foram marcadas agora")
    @ApiResponse(responseCode = "400", description = "Cuidador sem pacienteId (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Paciente informando outro paciente (ACESSO_NEGADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<MensagensLidasResponseDTO> marcarComoLidas(
            @Parameter(description = "Paciente da conversa (obrigatório para o cuidador)") @RequestParam(required = false)
            Long pacienteId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(new MensagensLidasResponseDTO(marcarMensagensComoLidasUseCase.executar(usuario, pacienteId)));
    }

    @GetMapping("/nao-lidas")
    @PerfilExigido(Perfil.CUIDADOR_OU_PACIENTE)
    @Operation(summary = "Total de mensagens recebidas e ainda não lidas",
            description = "Cuidador: somando as conversas de todos os seus pacientes. Paciente: as do cuidador.")
    @ApiResponse(responseCode = "200", description = "Contador")
    public ResponseEntity<NaoLidasResponseDTO> naoLidas(@Parameter(hidden = true)
                                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(new NaoLidasResponseDTO(contarMensagensNaoLidasUseCase.executar(usuario)));
    }
}
