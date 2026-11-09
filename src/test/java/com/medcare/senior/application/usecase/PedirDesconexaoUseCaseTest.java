package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.desconexao.NotificadorDePedidoDeDesconexaoPort;
import com.medcare.senior.domain.desconexao.PedidoDesconexao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoNotificacao;
import com.medcare.senior.domain.desconexao.PedidoDesconexaoRepositoryPort;
import com.medcare.senior.domain.desconexao.StatusPedidoDesconexao;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.notificacao.ResultadoEnvioPush;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PedirDesconexaoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 11, 9, 14, 30);

    @Mock
    private PedidoDesconexaoRepositoryPort pedidoRepository;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private NotificadorDePedidoDeDesconexaoPort notificador;

    private PedirDesconexaoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = criar(24);
    }

    private PedirDesconexaoUseCase criar(int validadeHoras) {
        return new PedirDesconexaoUseCase(pedidoRepository, pacienteRepository, notificador,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO), validadeHoras);
    }

    private void pacienteExiste() {
        when(pacienteRepository.findById(PACIENTE_ID)).thenReturn(Optional.of(
                Paciente.builder().id(PACIENTE_ID).nome("Maria").cuidadorId(CUIDADOR_ID).build()));
    }

    private static PedidoDesconexao gravado(PedidoDesconexao pedido, long id) {
        return new PedidoDesconexao(id, pedido.pacienteId(), pedido.status(), pedido.criadoEm(), pedido.expiraEm(),
                null, null);
    }

    @Test
    void criaPedidoPendenteDe24hEAvisaOCuidador() {
        pacienteExiste();
        when(pedidoRepository.findPendente(PACIENTE_ID)).thenReturn(Optional.empty());
        when(pedidoRepository.inserirPendente(any())).thenAnswer(i -> Optional.of(gravado(i.getArgument(0), 7L)));

        PedirDesconexaoUseCase.Resultado resultado = useCase.executar(PACIENTE_ID);

        assertThat(resultado.criado()).isTrue();
        assertThat(resultado.pedido().id()).isEqualTo(7L);
        assertThat(resultado.pedido().status()).isEqualTo(StatusPedidoDesconexao.PENDENTE);
        assertThat(resultado.pedido().criadoEm()).isEqualTo(AGORA);
        assertThat(resultado.pedido().expiraEm()).isEqualTo(AGORA.plusHours(24));
        // Um pendente vencido do paciente é expirado antes, para não bloquear o novo
        verify(pedidoRepository).expirarVencidos(PACIENTE_ID, AGORA);
        ArgumentCaptor<PedidoDesconexaoNotificacao> aviso = ArgumentCaptor.forClass(PedidoDesconexaoNotificacao.class);
        verify(notificador).notificar(aviso.capture());
        assertThat(aviso.getValue()).isEqualTo(new PedidoDesconexaoNotificacao(7L, PACIENTE_ID, CUIDADOR_ID, "Maria"));
    }

    @Test
    void comPedidoPendenteDevolveOMesmoSemDuplicarNemAvisarDeNovo() {
        pacienteExiste();
        PedidoDesconexao existente = gravado(PedidoDesconexao.novo(PACIENTE_ID, AGORA.minusHours(1), Duration.ofHours(24)), 3L);
        when(pedidoRepository.findPendente(PACIENTE_ID)).thenReturn(Optional.of(existente));

        PedirDesconexaoUseCase.Resultado resultado = useCase.executar(PACIENTE_ID);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.pedido()).isEqualTo(existente);
        verify(pedidoRepository, never()).inserirPendente(any());
        verify(notificador, never()).notificar(any());
    }

    @Test
    void pedidoSimultaneoQuePerdeACorridaDevolveOQueFicou() {
        pacienteExiste();
        PedidoDesconexao vencedor = gravado(PedidoDesconexao.novo(PACIENTE_ID, AGORA, Duration.ofHours(24)), 9L);
        when(pedidoRepository.findPendente(PACIENTE_ID)).thenReturn(Optional.empty(), Optional.of(vencedor));
        when(pedidoRepository.inserirPendente(any())).thenReturn(Optional.empty());

        PedirDesconexaoUseCase.Resultado resultado = useCase.executar(PACIENTE_ID);

        assertThat(resultado.criado()).isFalse();
        assertThat(resultado.pedido()).isEqualTo(vencedor);
        verify(notificador, never()).notificar(any());
    }

    @Test
    void falhaDoAvisoNaoDerrubaOPedido() {
        pacienteExiste();
        when(pedidoRepository.findPendente(PACIENTE_ID)).thenReturn(Optional.empty());
        when(pedidoRepository.inserirPendente(any())).thenAnswer(i -> Optional.of(gravado(i.getArgument(0), 7L)));
        when(notificador.notificar(any())).thenThrow(new IllegalStateException("FCM fora do ar"));

        assertThat(useCase.executar(PACIENTE_ID).criado()).isTrue();
    }

    @Test
    void avisoSemTokenOuPushDesligadoTambemNaoAtrapalha() {
        pacienteExiste();
        when(pedidoRepository.findPendente(PACIENTE_ID)).thenReturn(Optional.empty());
        when(pedidoRepository.inserirPendente(any())).thenAnswer(i -> Optional.of(gravado(i.getArgument(0), 7L)));
        when(notificador.notificar(any())).thenReturn(ResultadoEnvioPush.PUSH_DESLIGADO);

        assertThat(useCase.executar(PACIENTE_ID).pedido().id()).isEqualTo(7L);
    }

    @Test
    void pacienteQueNaoExisteMaisTemSessaoInvalida() {
        when(pacienteRepository.findById(PACIENTE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID)).isInstanceOf(SessaoInvalidaException.class);
        verify(pedidoRepository, never()).inserirPendente(any());
    }

    @Test
    void validadeConfiguravelDe1A72Horas() {
        pacienteExiste();
        when(pedidoRepository.findPendente(PACIENTE_ID)).thenReturn(Optional.empty());
        when(pedidoRepository.inserirPendente(any())).thenAnswer(i -> Optional.of(gravado(i.getArgument(0), 7L)));

        assertThat(criar(1).executar(PACIENTE_ID).pedido().expiraEm()).isEqualTo(AGORA.plusHours(1));
        assertThat(criar(72)).isNotNull();
        assertThatThrownBy(() -> criar(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> criar(73)).isInstanceOf(IllegalArgumentException.class);
    }
}
