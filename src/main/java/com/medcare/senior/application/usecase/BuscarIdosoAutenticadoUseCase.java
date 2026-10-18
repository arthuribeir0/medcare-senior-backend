package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Dados do idoso autenticado (token IDOSO) e o nome de quem cuida dele.
 */
@Service
@RequiredArgsConstructor
public class BuscarIdosoAutenticadoUseCase {

    private final IdosoRepositoryPort idosoRepository;
    private final CuidadorRepositoryPort cuidadorRepository;

    /**
     * @param idosoId ID do idoso autenticado, extraído do JWT
     * @throws SessaoInvalidaException se o idoso não existir mais (token ainda válido de conta excluída)
     */
    public Resultado executar(Long idosoId) {
        Idoso idoso = idosoRepository.findById(idosoId).orElseThrow(SessaoInvalidaException::new);
        String cuidadorNome = cuidadorRepository.findById(idoso.getCuidadorId())
                .map(Cuidador::getNome)
                .orElseThrow(SessaoInvalidaException::new);
        return new Resultado(idoso, cuidadorNome);
    }

    public record Resultado(Idoso idoso, String cuidadorNome) {
    }
}
