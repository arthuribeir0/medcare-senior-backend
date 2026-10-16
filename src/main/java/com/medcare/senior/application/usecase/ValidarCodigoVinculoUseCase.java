package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ValidarCodigoVinculoUseCase {

    private final CodigoVinculoRepositoryPort codigoVinculoRepository;
    private final TokenGeneratorPort tokenGenerator;
    private final Clock clock;

    /**
     * @return o JWT do idoso vinculado ao código
     * @throws CodigoVinculoInvalidoException se o código não existir, estiver expirado ou já tiver sido usado
     */
    public String executar(String codigo) {
        LocalDateTime agora = LocalDateTime.now(clock);

        CodigoVinculo codigoVinculo = codigoVinculoRepository.findMaisRecenteByCodigoUnico(codigo.trim())
                .orElseThrow(CodigoVinculoInvalidoException::new);

        if (codigoVinculo.isUsado() || !codigoVinculo.getExpiraEm().isAfter(agora)) {
            throw new CodigoVinculoInvalidoException();
        }

        // Marcação atômica (UPDATE condicional): se duas requisições chegarem juntas com o mesmo código,
        // só uma delas consegue marcá-lo como usado e recebe o token.
        if (!codigoVinculoRepository.marcarComoUsado(codigoVinculo.getId(), agora)) {
            throw new CodigoVinculoInvalidoException();
        }

        return tokenGenerator.generateToken(codigoVinculo.getIdosoId(), Role.IDOSO);
    }
}
