package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.SessaoAutenticada;
import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
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
    private final IdosoRepositoryPort idosoRepository;
    private final Clock clock;

    /**
     * @return o JWT, o ID e o nome do idoso vinculado ao código
     * @throws CodigoVinculoInvalidoException se o código não existir, estiver expirado ou já tiver sido usado
     */
    public SessaoAutenticada executar(String codigo) {
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

        // Uma consulta só, e só depois do código consumido (tentativas inválidas não chegam aqui). O idoso sempre
        // existe enquanto o código existe (FK); por segurança, ausência responde como código inválido.
        Idoso idoso = idosoRepository.findByIdAndCuidadorId(codigoVinculo.getIdosoId(), codigoVinculo.getCuidadorId())
                .orElseThrow(CodigoVinculoInvalidoException::new);

        return new SessaoAutenticada(tokenGenerator.generateToken(idoso.getId(), Role.IDOSO), idoso.getId(),
                idoso.getNome());
    }
}
