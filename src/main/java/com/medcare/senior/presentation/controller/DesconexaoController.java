package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.CancelarPedidoDesconexaoUseCase;
import com.medcare.senior.application.usecase.ConsultarPedidoDesconexaoUseCase;
import com.medcare.senior.application.usecase.DecidirPedidoDesconexaoUseCase;
import com.medcare.senior.application.usecase.DesconectarAparelhoDoPacienteUseCase;
import com.medcare.senior.application.usecase.ListarPedidosDesconexaoPendentesUseCase;
import com.medcare.senior.application.usecase.PedirDesconexaoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.presentation.dto.PedidoDesconexaoPendenteResponseDTO;
import com.medcare.senior.presentation.dto.PedidoDesconexaoResponseDTO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Saída da conta do paciente com autorização do cuidador: o paciente pede, o cuidador vinculado autoriza ou recusa, e
 * só com a autorização o aparelho é desconectado (o próximo pedido dele recebe 401 SESSAO_INVALIDA). O cuidador também
 * pode desconectar o aparelho sem pedido. O vínculo continua: para voltar, um código novo.
 *
 * <p>As rotas do paciente ficam em /api/pacientes/me/** (regras do SecurityConfig antes de /api/pacientes/**); os
 * mapeamentos literais têm precedência sobre /api/pacientes/{id}.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Pacientes")
public class DesconexaoController {

    private final PedirDesconexaoUseCase pedirDesconexaoUseCase;
    private final ConsultarPedidoDesconexaoUseCase consultarPedidoDesconexaoUseCase;
    private final CancelarPedidoDesconexaoUseCase cancelarPedidoDesconexaoUseCase;
    private final ListarPedidosDesconexaoPendentesUseCase listarPedidosDesconexaoPendentesUseCase;
    private final DecidirPedidoDesconexaoUseCase decidirPedidoDesconexaoUseCase;
    private final DesconectarAparelhoDoPacienteUseCase desconectarAparelhoDoPacienteUseCase;

    @PostMapping("/api/pacientes/me/pedido-desconexao")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "O paciente pede para sair da conta neste aparelho",
            description = "O cuidador é avisado (push, quando ligado) e decide. Só um pedido pendente por vez: pedir de "
                    + "novo devolve o mesmo (200), sem duplicar nem avisar outra vez. Vale 24 horas (DESCONEXAO_VALIDADE_HORAS).")
    @ApiResponse(responseCode = "201", description = "Pedido criado; o cuidador foi avisado")
    @ApiResponse(responseCode = "200", description = "Já havia um pedido pendente; ele é devolvido")
    public ResponseEntity<PedidoDesconexaoResponseDTO> pedir(@Parameter(hidden = true)
                                                             @AuthenticationPrincipal AuthenticatedUser usuario) {
        PedirDesconexaoUseCase.Resultado resultado = pedirDesconexaoUseCase.executar(usuario.pacienteId());
        return ResponseEntity.status(resultado.criado() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(PedidoDesconexaoResponseDTO.from(resultado.pedido()));
    }

    @GetMapping("/api/pacientes/me/pedido-desconexao")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "Pedido mais recente do paciente para sair da conta",
            description = "Para a tela de espera do app. Depois de AUTORIZADO, o token do aparelho deixa de valer: esta "
                    + "e qualquer outra rota respondem 401 SESSAO_INVALIDA.")
    @ApiResponse(responseCode = "200", description = "Pedido mais recente, com o status valendo agora")
    @ApiResponse(responseCode = "404", description = "O paciente nunca pediu para sair (RECURSO_NAO_ENCONTRADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<PedidoDesconexaoResponseDTO> consultar(@Parameter(hidden = true)
                                                                 @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(PedidoDesconexaoResponseDTO.from(consultarPedidoDesconexaoUseCase.executar(usuario.pacienteId())));
    }

    @DeleteMapping("/api/pacientes/me/pedido-desconexao")
    @PerfilExigido(Perfil.PACIENTE)
    @Operation(summary = "O paciente desiste do pedido para sair da conta",
            description = "Idempotente: sem pedido pendente, também responde 204.")
    @ApiResponse(responseCode = "204", description = "Pedido cancelado (ou não havia pendente)")
    public ResponseEntity<Void> cancelar(@Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        cancelarPedidoDesconexaoUseCase.executar(usuario.pacienteId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/pacientes/pedidos-desconexao")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Pedidos dos pacientes do cuidador para sair da conta, aguardando decisão",
            description = "Só os pendentes dentro do prazo, dos mais antigos para os mais novos.")
    @ApiResponse(responseCode = "200", description = "Pedidos pendentes (pode ser vazia)")
    public ResponseEntity<List<PedidoDesconexaoPendenteResponseDTO>> listarPendentes(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(listarPedidosDesconexaoPendentesUseCase.executar(usuario.cuidadorId()).stream()
                .map(PedidoDesconexaoPendenteResponseDTO::from)
                .toList());
    }

    @PutMapping("/api/pacientes/{id}/pedidos-desconexao/{pedidoId}/autorizar")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Autoriza o pedido do paciente para sair da conta",
            description = "Desconecta o aparelho do paciente: o token dele deixa de valer e os códigos de vínculo não usados "
                    + "são inutilizados. O vínculo, os remédios e o histórico continuam; para voltar, gere um código novo. "
                    + "Autorizar de novo devolve o pedido como está.")
    @ApiResponse(responseCode = "200", description = "Pedido autorizado")
    @ApiResponse(responseCode = "404", description = "Paciente ou pedido inexistente, ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "Pedido já recusado, cancelado ou vencido (PEDIDO_ENCERRADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<PedidoDesconexaoResponseDTO> autorizar(
            @Parameter(description = "ID do paciente") @PathVariable Long id,
            @Parameter(description = "ID do pedido") @PathVariable Long pedidoId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(PedidoDesconexaoResponseDTO.from(
                decidirPedidoDesconexaoUseCase.autorizar(id, pedidoId, usuario.cuidadorId())));
    }

    @PutMapping("/api/pacientes/{id}/pedidos-desconexao/{pedidoId}/recusar")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Recusa o pedido do paciente para sair da conta",
            description = "O paciente continua conectado. Recusar de novo devolve o pedido como está.")
    @ApiResponse(responseCode = "200", description = "Pedido recusado")
    @ApiResponse(responseCode = "404", description = "Paciente ou pedido inexistente, ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "Pedido já autorizado, cancelado ou vencido (PEDIDO_ENCERRADO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<PedidoDesconexaoResponseDTO> recusar(
            @Parameter(description = "ID do paciente") @PathVariable Long id,
            @Parameter(description = "ID do pedido") @PathVariable Long pedidoId,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser usuario) {
        return ResponseEntity.ok(PedidoDesconexaoResponseDTO.from(
                decidirPedidoDesconexaoUseCase.recusar(id, pedidoId, usuario.cuidadorId())));
    }

    @PostMapping("/api/pacientes/{id}/desconectar-aparelho")
    @PerfilExigido(Perfil.CUIDADOR)
    @Operation(summary = "Desconecta o aparelho do paciente sem esperar um pedido",
            description = "Mesmo efeito da autorização (ex.: celular perdido ou trocado). Um pedido pendente fica como "
                    + "AUTORIZADO. Idempotente.")
    @ApiResponse(responseCode = "204", description = "Aparelho desconectado")
    @ApiResponse(responseCode = "404", description = "Paciente inexistente ou de outro cuidador",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<Void> desconectarAparelho(@Parameter(description = "ID do paciente") @PathVariable Long id,
                                                    @Parameter(hidden = true)
                                                    @AuthenticationPrincipal AuthenticatedUser usuario) {
        desconectarAparelhoDoPacienteUseCase.executar(id, usuario.cuidadorId());
        return ResponseEntity.noContent().build();
    }
}
