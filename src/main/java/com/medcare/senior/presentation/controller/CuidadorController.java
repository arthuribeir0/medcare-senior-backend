package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.CadastrarCuidadorUseCase;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.presentation.dto.CuidadorRequestDTO;
import com.medcare.senior.presentation.dto.CuidadorResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cuidadores")
@RequiredArgsConstructor
public class CuidadorController {

    private final CadastrarCuidadorUseCase cadastrarCuidadorUseCase;

    @PostMapping
    public ResponseEntity<CuidadorResponseDTO> cadastrar(@Valid @RequestBody CuidadorRequestDTO request) {
        Cuidador cuidador = cadastrarCuidadorUseCase.executar(
                request.nome(), request.email(), request.senha(), request.telefone());
        return ResponseEntity.status(HttpStatus.CREATED).body(CuidadorResponseDTO.from(cuidador));
    }
}
