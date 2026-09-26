package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.AutenticarCuidadorUseCase;
import com.medcare.senior.presentation.dto.AuthResponseDTO;
import com.medcare.senior.presentation.dto.LoginRequestDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AutenticarCuidadorUseCase autenticarCuidadorUseCase;

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        String token = autenticarCuidadorUseCase.executar(request.email(), request.senha());
        return ResponseEntity.ok(AuthResponseDTO.bearer(token));
    }
}
