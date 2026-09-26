package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.CadastrarIdosoUseCase;
import com.medcare.senior.application.usecase.GerarCodigoVinculoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.presentation.dto.CodigoVinculoResponseDTO;
import com.medcare.senior.presentation.dto.IdosoRequestDTO;
import com.medcare.senior.presentation.dto.IdosoResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Acesso restrito a CUIDADOR no SecurityConfig. O ID do cuidador vem sempre do JWT
 * (principal preenchido pelo JwtAuthenticationFilter), nunca do corpo da requisição.
 */
@RestController
@RequestMapping("/api/idosos")
@RequiredArgsConstructor
public class IdosoController {

    private final CadastrarIdosoUseCase cadastrarIdosoUseCase;
    private final GerarCodigoVinculoUseCase gerarCodigoVinculoUseCase;

    @PostMapping
    public ResponseEntity<IdosoResponseDTO> cadastrar(@Valid @RequestBody IdosoRequestDTO request,
                                                      @AuthenticationPrincipal AuthenticatedUser usuario) {
        Idoso idoso = cadastrarIdosoUseCase.executar(
                request.nome(), request.dataNascimento(), request.telefone(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(IdosoResponseDTO.from(idoso));
    }

    @PostMapping("/{id}/gerar-codigo")
    public ResponseEntity<CodigoVinculoResponseDTO> gerarCodigo(@PathVariable Long id,
                                                                @AuthenticationPrincipal AuthenticatedUser usuario) {
        CodigoVinculo codigo = gerarCodigoVinculoUseCase.executar(id, usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(CodigoVinculoResponseDTO.from(codigo));
    }
}
