package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.ConfirmarDoseUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.presentation.dto.DoseResponseDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * IDOSO e CUIDADOR no SecurityConfig. O ID vem sempre do JWT: o idoso só confirma as próprias doses; o cuidador, só as
 * dos idosos dele (o use case confere o dono e responde 404 para dose alheia).
 */
@RestController
@RequestMapping("/api/doses")
@RequiredArgsConstructor
@Tag(name = "Doses", description = "Confirmação de dose pelo idoso ou pelo cuidador dele")
public class DoseController {

    private final ConfirmarDoseUseCase confirmarDoseUseCase;

    @PutMapping("/{id}/confirmar")
    @Operation(summary = "Confirma que o idoso tomou a dose",
            description = "Token IDOSO: só a própria dose. Token CUIDADOR: só dose de idoso dele. Dentro da janela "
                    + "[abreEm, limiteConfirmacao]: CONFIRMADA_NO_HORARIO até avisoEm (inclusive) e "
                    + "CONFIRMADA_COM_ATRASO depois. A hora da confirmação é a do servidor e confirmadoPor registra quem "
                    + "confirmou (IDOSO ou CUIDADOR). Dose já registrada nunca muda (RN-008).")
    @ApiResponse(responseCode = "200", description = "Dose confirmada")
    @ApiResponse(responseCode = "401", description = "Token ausente ou inválido; ou conta do token não existe mais "
            + "(codigo SESSAO_INVALIDA)", content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "403", description = "Token sem o papel IDOSO nem CUIDADOR", content = @Content)
    @ApiResponse(responseCode = "404", description = "Dose inexistente, de outro idoso, de idoso de outro cuidador ou de "
            + "remédio removido",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "DOSE_JA_REGISTRADA, DOSE_MUITO_CEDO ou DOSE_ENCERRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<DoseResponseDTO> confirmar(@Parameter(description = "ID da dose") @PathVariable Long id,
                                                     @Parameter(hidden = true)
                                                     @AuthenticationPrincipal AuthenticatedUser usuario) {
        DoseAvaliada confirmada = usuario.isCuidador()
                ? confirmarDoseUseCase.executarComoCuidador(id, usuario.cuidadorId())
                : confirmarDoseUseCase.executar(id, usuario.idosoId());
        return ResponseEntity.ok(DoseResponseDTO.from(confirmada));
    }
}
