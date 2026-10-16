package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GerarCodigoVinculoUseCase {

    private static final Duration VALIDADE = Duration.ofHours(24);
    private static final int MAX_TENTATIVAS = 10;

    private final SecureRandom secureRandom = new SecureRandom();

    private final IdosoRepositoryPort idosoRepository;
    private final CodigoVinculoRepositoryPort codigoVinculoRepository;
    private final Clock clock;

    /**
     * @param idosoId    idoso para o qual o código será gerado
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public CodigoVinculo executar(Long idosoId, Long cuidadorId) {
        // Busca já filtrando pelo dono: idoso de outro cuidador responde igual a idoso inexistente,
        // para não revelar quais IDs existem.
        Idoso idoso = idosoRepository.findByIdAndCuidadorId(idosoId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Idoso não encontrado"));

        LocalDateTime agora = LocalDateTime.now(clock);

        CodigoVinculo codigo = CodigoVinculo.builder()
                .codigoUnico(gerarCodigoDisponivel(agora))
                .cuidadorId(idoso.getCuidadorId())
                .idosoId(idoso.getId())
                .expiraEm(agora.plus(VALIDADE))
                .usado(false)
                .build();

        return codigoVinculoRepository.save(codigo);
    }

    private String gerarCodigoDisponivel(LocalDateTime agora) {
        for (int i = 0; i < MAX_TENTATIVAS; i++) {
            String candidato = String.format("%06d", secureRandom.nextInt(1_000_000));
            if (!codigoVinculoRepository.existsCodigoAtivo(candidato, agora)) {
                return candidato;
            }
        }
        throw new IllegalStateException("Não foi possível gerar um código de vínculo único");
    }
}
