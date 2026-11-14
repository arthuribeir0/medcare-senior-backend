package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.DesconexaoDoAparelhoService;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * O cuidador desconecta o aparelho do paciente por conta própria (ex.: celular perdido ou trocado), sem esperar um
 * pedido. Mesmo efeito da autorização; um pedido pendente, se houver, fica como AUTORIZADO por este cuidador.
 * Idempotente: desconectar de novo só renova o marcador.
 */
@Service
@RequiredArgsConstructor
public class DesconectarAparelhoDoPacienteUseCase {

    private final PacienteRepositoryPort pacienteRepository;
    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final DesconexaoDoAparelhoService desconexaoDoAparelho;
    private final Clock clock;

    /**
     * @throws RecursoNaoEncontradoException se o paciente não existir ou for de outro cuidador
     */
    @Transactional
    public void executar(Long pacienteId, Long cuidadorId) {
        pacienteRepository.findByIdAndCuidadorId(pacienteId, cuidadorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Paciente não encontrado"));
        pedidoRepository.autorizarPendente(pacienteId, cuidadorId, LocalDateTime.now(clock));
        desconexaoDoAparelho.desconectar(pacienteId);
    }
}
