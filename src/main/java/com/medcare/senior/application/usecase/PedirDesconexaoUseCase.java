package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.NotificadorDePedidoDeDesconexaoPort;
import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoNotificacao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * O paciente pede para sair da conta no aparelho; o cuidador vinculado é avisado e decide. No máximo um pedido
 * pendente por paciente: pedir de novo com um pendente válido devolve o mesmo, sem duplicar nem avisar outra vez.
 *
 * <p>O pedido vale {@code app.desconexao.validade-horas} horas (padrão {@value #VALIDADE_PADRAO_HORAS}, de
 * {@value #VALIDADE_MINIMA_HORAS} a {@value #VALIDADE_MAXIMA_HORAS}; valor fora disso impede a subida).
 *
 * <p>Sem transação envolvendo tudo de propósito: o pedido é gravado antes do aviso, e uma falha do push (ou do próprio
 * notificador) não desfaz o pedido. O cuidador vê o pedido no Painel de qualquer forma.
 */
@Service
public class PedirDesconexaoUseCase {

    public static final int VALIDADE_PADRAO_HORAS = 24;
    public static final int VALIDADE_MINIMA_HORAS = 1;
    public static final int VALIDADE_MAXIMA_HORAS = 72;

    private static final Logger log = LoggerFactory.getLogger(PedirDesconexaoUseCase.class);

    private final PedidoDesconexaoRepositoryPort pedidoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final NotificadorDePedidoDeDesconexaoPort notificador;
    private final Clock clock;
    private final Duration validade;

    public PedirDesconexaoUseCase(PedidoDesconexaoRepositoryPort pedidoRepository,
                                  PacienteRepositoryPort pacienteRepository,
                                  NotificadorDePedidoDeDesconexaoPort notificador,
                                  Clock clock,
                                  @Value("${app.desconexao.validade-horas:" + VALIDADE_PADRAO_HORAS + "}") int validadeHoras) {
        if (validadeHoras < VALIDADE_MINIMA_HORAS || validadeHoras > VALIDADE_MAXIMA_HORAS) {
            throw new IllegalArgumentException("A validade do pedido de desconexão deve ser de " + VALIDADE_MINIMA_HORAS
                    + " a " + VALIDADE_MAXIMA_HORAS + " horas");
        }
        this.pedidoRepository = pedidoRepository;
        this.pacienteRepository = pacienteRepository;
        this.notificador = notificador;
        this.clock = clock;
        this.validade = Duration.ofHours(validadeHoras);
    }

    /**
     * @param pacienteId ID do paciente autenticado, extraído do JWT
     * @throws SessaoInvalidaException se o paciente não existir mais
     */
    public Resultado executar(Long pacienteId) {
        Paciente paciente = pacienteRepository.findById(pacienteId).orElseThrow(SessaoInvalidaException::new);
        LocalDateTime agora = LocalDateTime.now(clock);
        // Um pendente vencido que o job ainda não marcou não pode bloquear o pedido novo (índice único)
        pedidoRepository.expirarVencidos(pacienteId, agora);

        Optional<PedidoDesconexao> pendente = pedidoRepository.findPendente(pacienteId);
        if (pendente.isPresent()) {
            return new Resultado(pendente.get(), false);
        }
        Optional<PedidoDesconexao> inserido = pedidoRepository.inserirPendente(PedidoDesconexao.novo(pacienteId, agora, validade));
        if (inserido.isEmpty()) {
            // Outro pedido do mesmo paciente entrou ao mesmo tempo: devolve o que ficou
            return new Resultado(pedidoRepository.findPendente(pacienteId).orElseThrow(), false);
        }
        PedidoDesconexao pedido = inserido.get();
        avisarCuidador(pedido, paciente);
        return new Resultado(pedido, true);
    }

    private void avisarCuidador(PedidoDesconexao pedido, Paciente paciente) {
        try {
            notificador.notificar(new PedidoDesconexaoNotificacao(pedido.id(), paciente.getId(), paciente.getCuidadorId(),
                    paciente.getNome()));
        } catch (RuntimeException e) {
            log.warn("Falha ao avisar o cuidador {} do pedido de desconexão {}: {}", paciente.getCuidadorId(), pedido.id(),
                    e.getClass().getSimpleName());
        }
    }

    /**
     * @param criado false quando já havia um pedido pendente (devolvido sem duplicar)
     */
    public record Resultado(PedidoDesconexao pedido, boolean criado) {
    }
}
