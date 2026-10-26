package com.medcare.senior.presentation.controller;

import com.medcare.senior.infrastructure.docs.PerfilExigido;
import com.medcare.senior.infrastructure.docs.PerfilExigido.Perfil;
import com.medcare.senior.application.usecase.ValidarCodigoVinculoUseCase;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.infrastructure.security.RateLimiter;
import com.medcare.senior.presentation.dto.AuthResponseDTO;
import com.medcare.senior.presentation.dto.ValidarCodigoRequestDTO;
import com.medcare.senior.presentation.exception.ErroResponse;
import com.medcare.senior.presentation.exception.MuitasTentativasException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ponto de entrada do paciente: troca um código de vínculo válido por um JWT com role PACIENTE.
 * Público (sem JWT) e protegido por rate limiting por IP.
 */
@RestController
@RequestMapping("/api/vinculo")
@RequiredArgsConstructor
@Tag(name = "Vínculo")
public class VinculoController {

    private final ValidarCodigoVinculoUseCase validarCodigoVinculoUseCase;
    private final RateLimiter rateLimiter;

    @PostMapping("/validar")
    @PerfilExigido(Perfil.PUBLICO)
    @Operation(summary = "Troca um código de vínculo por um JWT do paciente",
            description = "Código de 6 dígitos gerado pelo cuidador, válido por 24 horas e de uso único. Depois de 5 "
                    + "tentativas inválidas em 15 minutos, o IP fica bloqueado até o fim desses 15 minutos (429 com "
                    + "Retry-After). Um acerto não zera o contador.")
    @ApiResponse(responseCode = "200", description = "Código válido; devolve o token e o id e o nome do paciente")
    @ApiResponse(responseCode = "400", description = "Código inexistente, expirado ou já usado, com a mesma resposta nos três casos "
            + "(CODIGO_INVALIDO); ou campo ausente ou fora do formato (VALIDACAO)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    @ApiResponse(responseCode = "429", description = "Muitas tentativas inválidas (LIMITE_TENTATIVAS, cabeçalho Retry-After)",
            content = @Content(schema = @Schema(implementation = ErroResponse.class)))
    public ResponseEntity<AuthResponseDTO> validar(@Valid @RequestBody ValidarCodigoRequestDTO request,
                                                   HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();

        long segundosAteLiberar = rateLimiter.segundosAteLiberar(ip);
        if (segundosAteLiberar > 0) {
            throw new MuitasTentativasException(segundosAteLiberar);
        }

        try {
            return ResponseEntity.ok(AuthResponseDTO.bearer(validarCodigoVinculoUseCase.executar(request.codigo())));
        } catch (CodigoVinculoInvalidoException e) {
            // Só falhas contam; um acerto NÃO zera o contador (senão um cuidador poderia gerar e validar
            // códigos próprios para "resetar" o limite e continuar testando códigos alheios)
            rateLimiter.registrarFalha(ip);
            throw e;
        }
    }
}
