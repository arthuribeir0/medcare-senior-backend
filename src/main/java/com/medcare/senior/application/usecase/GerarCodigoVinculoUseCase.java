package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
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

    private final PacienteRepositoryPort pacienteRepository;
    private final CodigoVinculoRepositoryPort codigoVinculoRepository;
    private final Clock clock;

    /**
     * @param pacienteId    paciente para o qual o código será gerado
     * @param cuidadorId ID do cuidador autenticado, extraído do JWT
     */
    public CodigoVinculo executar(Long pacienteId, Long cuidadorId) {
        // Busca já filtrando pelo dono: paciente de outro cuidador responde igual a paciente inexistente,
        // para não revelar quais IDs existem.
        Paciente paciente = pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));

        LocalDateTime agora = LocalDateTime.now(clock);

        CodigoVinculo codigo = CodigoVinculo.builder()
                .codigoUnico(gerarCodigoDisponivel(agora))
                .cuidadorId(paciente.getCuidadorId())
                .pacienteId(paciente.getId())
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
