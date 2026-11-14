/*
 * Testes automatizados de decidir pedido desconexão (7 casos). Cobre: autorizar decide e desconecta o aparelho;
 * recusar decide sem desconectar; autorizar de novo devolve o pedido sem repetir o efeito; decidir o contrario de
 * um pedido já decidido responde 409; pedido cancelado ou vencido responde 409; paciente de outro cuidador
 * responde 404 sem tocar no pedido; pedido de outro paciente responde 404 sem decidir.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.DesconexaoDoAparelhoService;
import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import com.medcare.senior.domain.exception.PedidoDesconexaoEncerradoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecidirPedidoDesconexaoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;
    private static final Long PEDIDO_ID = 7L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 9, 14, 30);

    @Mock
    private PedidoDesconexaoRepositoryPort pedidoRepository;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private DesconexaoDoAparelhoService desconexaoDoAparelho;

    private DecidirPedidoDesconexaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DecidirPedidoDesconexaoUseCase(pedidoRepository, pacienteRepository, desconexaoDoAparelho,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private void pacienteDoCuidador() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
    }

    private void pedidoNoBanco(StatusPedidoDesconexao status) {
        LocalDateTime decidido = status == StatusPedidoDesconexao.PENDENTE ? null : AGORA.minusMinutes(1);
        when(pedidoRepository.findByIdAndPacienteId(PEDIDO_ID, PACIENTE_ID)).thenReturn(Optional.of(
                new PedidoDesconexao(PEDIDO_ID, PACIENTE_ID, status, AGORA.minusHours(1), AGORA.plusHours(23), decidido,
                        decidido == null ? null : CUIDADOR_ID)));
    }

    @Test
    void autorizarDecideEDesconectaOAparelho() {
        pacienteDoCuidador();
        pedidoNoBanco(StatusPedidoDesconexao.PENDENTE);
        when(pedidoRepository.decidir(PEDIDO_ID, StatusPedidoDesconexao.AUTORIZADO, CUIDADOR_ID, AGORA)).thenReturn(true);

        useCase.autorizar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID);

        verify(desconexaoDoAparelho).desconectar(PACIENTE_ID);
    }

    @Test
    void recusarDecideSemDesconectar() {
        pacienteDoCuidador();
        pedidoNoBanco(StatusPedidoDesconexao.PENDENTE);
        when(pedidoRepository.decidir(PEDIDO_ID, StatusPedidoDesconexao.RECUSADO, CUIDADOR_ID, AGORA)).thenReturn(true);

        useCase.recusar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID);

        verify(desconexaoDoAparelho, never()).desconectar(anyLong());
    }

    @Test
    void autorizarDeNovoDevolveOPedidoSemRepetirOEfeito() {
        pacienteDoCuidador();
        pedidoNoBanco(StatusPedidoDesconexao.AUTORIZADO);
        when(pedidoRepository.decidir(PEDIDO_ID, StatusPedidoDesconexao.AUTORIZADO, CUIDADOR_ID, AGORA)).thenReturn(false);

        PedidoDesconexao pedido = useCase.autorizar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID);

        assertThat(pedido.status()).isEqualTo(StatusPedidoDesconexao.AUTORIZADO);
        verify(desconexaoDoAparelho, never()).desconectar(anyLong());
    }

    @Test
    void decidirOContrarioDeUmPedidoJaDecididoResponde409() {
        pacienteDoCuidador();
        pedidoNoBanco(StatusPedidoDesconexao.RECUSADO);
        when(pedidoRepository.decidir(PEDIDO_ID, StatusPedidoDesconexao.AUTORIZADO, CUIDADOR_ID, AGORA)).thenReturn(false);

        assertThatThrownBy(() -> useCase.autorizar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID))
                .isInstanceOf(PedidoDesconexaoEncerradoException.class);
        verify(desconexaoDoAparelho, never()).desconectar(anyLong());
    }

    @Test
    void pedidoCanceladoOuVencidoResponde409() {
        pacienteDoCuidador();
        pedidoNoBanco(StatusPedidoDesconexao.CANCELADO);
        when(pedidoRepository.decidir(PEDIDO_ID, StatusPedidoDesconexao.RECUSADO, CUIDADOR_ID, AGORA)).thenReturn(false);

        assertThatThrownBy(() -> useCase.recusar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID))
                .isInstanceOf(PedidoDesconexaoEncerradoException.class);
    }

    @Test
    void pacienteDeOutroCuidadorResponde404SemTocarNoPedido() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.autorizar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(pedidoRepository, never()).decidir(anyLong(), any(), anyLong(), any());
    }

    @Test
    void pedidoDeOutroPacienteResponde404SemDecidir() {
        pacienteDoCuidador();
        when(pedidoRepository.findByIdAndPacienteId(PEDIDO_ID, PACIENTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.autorizar(PACIENTE_ID, PEDIDO_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verify(pedidoRepository, never()).decidir(anyLong(), any(), anyLong(), any());
    }
}
