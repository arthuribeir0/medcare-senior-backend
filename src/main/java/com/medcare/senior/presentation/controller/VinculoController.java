package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.ValidarCodigoVinculoUseCase;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.infrastructure.security.RateLimiter;
import com.medcare.senior.presentation.dto.AuthResponseDTO;
import com.medcare.senior.presentation.dto.ValidarCodigoRequestDTO;
import com.medcare.senior.presentation.exception.MuitasTentativasException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ponto de entrada do idoso: troca um código de vínculo válido por um JWT com role IDOSO.
 * Público (sem JWT) e protegido por rate limiting por IP.
 */
@RestController
@RequestMapping("/api/vinculo")
@RequiredArgsConstructor
public class VinculoController {

    private final ValidarCodigoVinculoUseCase validarCodigoVinculoUseCase;
    private final RateLimiter rateLimiter;

    @PostMapping("/validar")
    public ResponseEntity<AuthResponseDTO> validar(@Valid @RequestBody ValidarCodigoRequestDTO request,
                                                   HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();

        long segundosAteLiberar = rateLimiter.segundosAteLiberar(ip);
        if (segundosAteLiberar > 0) {
            throw new MuitasTentativasException(segundosAteLiberar);
        }

        try {
            String token = validarCodigoVinculoUseCase.executar(request.codigo());
            return ResponseEntity.ok(AuthResponseDTO.bearer(token));
        } catch (CodigoVinculoInvalidoException e) {
            // Só falhas contam; um acerto NÃO zera o contador (senão um cuidador poderia gerar e validar
            // códigos próprios para "resetar" o limite e continuar testando códigos alheios)
            rateLimiter.registrarFalha(ip);
            throw e;
        }
    }
}
