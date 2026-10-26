package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
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
 * PACIENTE e CUIDADOR no SecurityConfig. O ID vem sempre do JWT: o paciente só confirma as próprias doses; o cuidador, só as
 * dos pacientes dele (o use case confere o dono e responde 404 para dose alheia).
 */
@RestController
@RequestMapping("/api/doses")
@RequiredArgsConstructor
@Tag(name = "Doses")
public class DoseController {

    private final ConfirmarDoseUseCase confirmarDoseUseCase;

    @PutMapping("/{id}/confirmar")
    @PerfilExigido(Perfil.CUIDADOR_OU_PACIENTE)
    @Operation(summary = "Confirma que o paciente tomou a dose",
            description = "Token PACIENTE: só a própria dose. Token CUIDADOR: só dose de paciente dele.\n\n"
                    + "Janela, calculada no servidor: abre 60 minutos antes do horário programado (antes disso, "
                    + "DOSE_MUITO_CEDO). Até o aviso ao cuidador (avisoEm = horário + tolerância do cuidador, padrão de 30 "
                    + "minutos), inclusive, a dose fica CONFIRMADA_NO_HORARIO; depois, CONFIRMADA_COM_ATRASO. A janela "
                    + "encerra 30 minutos depois do aviso (limiteConfirmacao); depois disso, DOSE_ENCERRADA, e a varredura "
                    + "marca a dose como NAO_TOMADA.\n\n"
                    + "A hora da confirmação é a do servidor e confirmadoPor registra quem confirmou (PACIENTE ou "
                    + "CUIDADOR). Dose já registrada nunca muda (DOSE_JA_REGISTRADA), inclusive em confirmações "
                    + "simultâneas.")
    @ApiResponse(responseCode = "200", description = "Dose confirmada")
    @ApiResponse(responseCode = "404", description = "Dose inexistente, de outro paciente, de paciente de outro cuidador ou de "
            + "remédio removido",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "409", description = "DOSE_JA_REGISTRADA, DOSE_MUITO_CEDO ou DOSE_ENCERRADA",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<DoseResponseDTO> confirmar(@Parameter(description = "ID da dose", example = "1") @PathVariable Long id,
                                                     @Parameter(hidden = true)
                                                     @AuthenticationPrincipal AuthenticatedUser usuario) {
        DoseAvaliada confirmada = usuario.isCuidador()
                ? confirmarDoseUseCase.executarComoCuidador(id, usuario.cuidadorId())
                : confirmarDoseUseCase.executar(id, usuario.pacienteId());
        return ResponseEntity.ok(DoseResponseDTO.from(confirmada));
    }
}
