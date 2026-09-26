package com.medcare.senior.presentation.controller;

import com.medcare.senior.application.usecase.CadastrarMedicamentoUseCase;
import com.medcare.senior.application.usecase.EditarMedicamentoUseCase;
import com.medcare.senior.application.usecase.ListarMedicamentosUseCase;
import com.medcare.senior.application.usecase.RemoverMedicamentoUseCase;
import com.medcare.senior.domain.autenticacao.AuthenticatedUser;
import com.medcare.senior.domain.medicamento.Medicamento;
import com.medcare.senior.presentation.dto.MedicamentoRequestDTO;
import com.medcare.senior.presentation.dto.MedicamentoResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Acesso restrito a CUIDADOR no SecurityConfig. O ID do cuidador vem sempre do JWT, e os use cases
 * verificam que o idoso de cada medicamento pertence a esse cuidador.
 */
@RestController
@RequestMapping("/api/medicamentos")
@RequiredArgsConstructor
public class MedicamentoController {

    private final CadastrarMedicamentoUseCase cadastrarMedicamentoUseCase;
    private final EditarMedicamentoUseCase editarMedicamentoUseCase;
    private final ListarMedicamentosUseCase listarMedicamentosUseCase;
    private final RemoverMedicamentoUseCase removerMedicamentoUseCase;

    @PostMapping
    public ResponseEntity<MedicamentoResponseDTO> cadastrar(@Valid @RequestBody MedicamentoRequestDTO request,
                                                            @AuthenticationPrincipal AuthenticatedUser usuario) {
        Medicamento medicamento = cadastrarMedicamentoUseCase.executar(request.toDomain(), usuario.cuidadorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(MedicamentoResponseDTO.from(medicamento));
    }

    @GetMapping
    public ResponseEntity<List<MedicamentoResponseDTO>> listar(@RequestParam Long idosoId,
                                                               @AuthenticationPrincipal AuthenticatedUser usuario) {
        List<MedicamentoResponseDTO> medicamentos = listarMedicamentosUseCase.executar(idosoId, usuario.cuidadorId()).stream()
                .map(MedicamentoResponseDTO::from)
                .toList();
        return ResponseEntity.ok(medicamentos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MedicamentoResponseDTO> editar(@PathVariable Long id,
                                                         @Valid @RequestBody MedicamentoRequestDTO request,
                                                         @AuthenticationPrincipal AuthenticatedUser usuario) {
        Medicamento medicamento = editarMedicamentoUseCase.executar(id, request.toDomain(), usuario.cuidadorId());
        return ResponseEntity.ok(MedicamentoResponseDTO.from(medicamento));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remover(@PathVariable Long id,
                                        @RequestParam(defaultValue = "false") boolean forcarRemocao,
                                        @AuthenticationPrincipal AuthenticatedUser usuario) {
        removerMedicamentoUseCase.executar(id, usuario.cuidadorId(), forcarRemocao);
        return ResponseEntity.noContent().build();
    }
}
