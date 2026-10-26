package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.SessaoAutenticada;
import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import com.medcare.senior.domain.usuario.Role;
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
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidarCodigoVinculoUseCaseTest {

    private static final Long CODIGO_ID = 5L;
    private static final Long PACIENTE_ID = 10L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 9, 30);

    @Mock
    private CodigoVinculoRepositoryPort codigoVinculoRepository;

    @Mock
    private TokenGeneratorPort tokenGenerator;

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    private ValidarCodigoVinculoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ValidarCodigoVinculoUseCase(codigoVinculoRepository, tokenGenerator, pacienteRepository,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static CodigoVinculo codigo(String valor, LocalDateTime expiraEm, boolean usado) {
        return CodigoVinculo.builder()
                .id(CODIGO_ID)
                .codigoUnico(valor)
                .cuidadorId(1L)
                .pacienteId(PACIENTE_ID)
                .expiraEm(expiraEm)
                .usado(usado)
                .build();
    }

    @Test
    void codigoValidoDeveGerarTokenComRolePacienteEDevolverIdENomeDoPaciente() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("123456"))
                .thenReturn(Optional.of(codigo("123456", AGORA.plusHours(1), false)));
        when(codigoVinculoRepository.marcarComoUsado(CODIGO_ID, AGORA)).thenReturn(true);
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, 1L))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).nome("José Souza").cuidadorId(1L).build()));
        when(tokenGenerator.generateToken(PACIENTE_ID, Role.PACIENTE)).thenReturn("jwt-paciente");

        SessaoAutenticada sessao = useCase.executar(" 123456 ");

        assertThat(sessao).isEqualTo(new SessaoAutenticada("jwt-paciente", PACIENTE_ID, "José Souza"));
        verify(tokenGenerator).generateToken(PACIENTE_ID, Role.PACIENTE);
    }

    @Test
    void codigoInexistenteDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar("000000")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(tokenGenerator, never()).generateToken(anyLong(), any());
        // Tentativa inválida não consulta o paciente
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void codigoExpiradoDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("111111"))
                .thenReturn(Optional.of(codigo("111111", AGORA.minusMinutes(1), false)));

        assertThatThrownBy(() -> useCase.executar("111111")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(codigoVinculoRepository, never()).marcarComoUsado(anyLong(), any());
        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void codigoQueExpiraExatamenteAgoraDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("444444"))
                .thenReturn(Optional.of(codigo("444444", AGORA, false)));

        assertThatThrownBy(() -> useCase.executar("444444")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(codigoVinculoRepository, never()).marcarComoUsado(anyLong(), any());
    }

    @Test
    void codigoJaUsadoDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("222222"))
                .thenReturn(Optional.of(codigo("222222", AGORA.plusHours(1), true)));

        assertThatThrownBy(() -> useCase.executar("222222")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(codigoVinculoRepository, never()).marcarComoUsado(anyLong(), any());
        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void codigoConsumidoPorRequisicaoConcorrenteDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("333333"))
                .thenReturn(Optional.of(codigo("333333", AGORA.plusHours(1), false)));
        when(codigoVinculoRepository.marcarComoUsado(eq(CODIGO_ID), any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar("333333")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void mensagemDeErroDeveSerGenericaEIgualNosTresCasosDeFalha() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("000000")).thenReturn(Optional.empty());
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("111111"))
                .thenReturn(Optional.of(codigo("111111", AGORA.minusMinutes(1), false)));
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("222222"))
                .thenReturn(Optional.of(codigo("222222", AGORA.plusHours(1), true)));

        String inexistente = catchThrowable(() -> useCase.executar("000000")).getMessage();
        String expirado = catchThrowable(() -> useCase.executar("111111")).getMessage();
        String usado = catchThrowable(() -> useCase.executar("222222")).getMessage();

        assertThat(inexistente).isEqualTo(expirado).isEqualTo(usado);
        assertThat(inexistente).doesNotContainIgnoringCase("usado")
                .doesNotContainIgnoringCase("não existe")
                .doesNotContainIgnoringCase("inexistente");
    }
}
