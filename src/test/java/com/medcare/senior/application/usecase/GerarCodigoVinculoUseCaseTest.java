package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarCodigoVinculoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 9, 30);

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private CodigoVinculoRepositoryPort codigoVinculoRepository;

    private GerarCodigoVinculoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GerarCodigoVinculoUseCase(pacienteRepository, codigoVinculoRepository,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private void pacienteDoCuidador() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).build()));
    }

    @Test
    void deveGerarCodigoDeSeisDigitosValidoPor24Horas() {
        pacienteDoCuidador();
        when(codigoVinculoRepository.existsCodigoAtivo(anyString(), any(LocalDateTime.class))).thenReturn(false);
        when(codigoVinculoRepository.save(any(CodigoVinculo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        CodigoVinculo resultado = useCase.executar(PACIENTE_ID, CUIDADOR_ID);

        assertThat(resultado.getCodigoUnico()).matches("\\d{6}");
        assertThat(resultado.getPacienteId()).isEqualTo(PACIENTE_ID);
        assertThat(resultado.getCuidadorId()).isEqualTo(CUIDADOR_ID);
        assertThat(resultado.isUsado()).isFalse();
        assertThat(resultado.getExpiraEm()).isEqualTo(AGORA.plusHours(24));
        // A busca por código ativo usa o mesmo "agora" do Clock
        verify(codigoVinculoRepository).existsCodigoAtivo(resultado.getCodigoUnico(), AGORA);
    }

    @Test
    void deveTentarOutroCodigoQuandoOSorteadoJaEstaAtivo() {
        pacienteDoCuidador();
        when(codigoVinculoRepository.existsCodigoAtivo(anyString(), any(LocalDateTime.class)))
                .thenReturn(true, true, false);
        when(codigoVinculoRepository.save(any(CodigoVinculo.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        useCase.executar(PACIENTE_ID, CUIDADOR_ID);

        verify(codigoVinculoRepository, times(3)).existsCodigoAtivo(anyString(), any(LocalDateTime.class));
        ArgumentCaptor<CodigoVinculo> captor = ArgumentCaptor.forClass(CodigoVinculo.class);
        verify(codigoVinculoRepository).save(captor.capture());
        assertThat(captor.getValue().getCodigoUnico()).matches("\\d{6}");
    }

    @Test
    void deveFalharDepoisDeDezColisoesSeguidas() {
        pacienteDoCuidador();
        when(codigoVinculoRepository.existsCodigoAtivo(anyString(), any(LocalDateTime.class))).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID, CUIDADOR_ID))
                .isInstanceOf(IllegalStateException.class);

        verify(codigoVinculoRepository, times(10)).existsCodigoAtivo(anyString(), any(LocalDateTime.class));
        verify(codigoVinculoRepository, never()).save(any());
    }

    @Test
    void deveRejeitarPacienteDeOutroCuidadorOuInexistente() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(PACIENTE_ID, CUIDADOR_ID))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Paciente não encontrado");

        verifyNoInteractions(codigoVinculoRepository);
    }
}
