package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.exception.CodigoVinculoInvalidoException;
import com.medcare.senior.domain.usuario.CodigoVinculo;
import com.medcare.senior.domain.usuario.CodigoVinculoRepositoryPort;
import com.medcare.senior.domain.usuario.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidarCodigoVinculoUseCaseTest {

    private static final Long CODIGO_ID = 5L;
    private static final Long IDOSO_ID = 10L;

    @Mock
    private CodigoVinculoRepositoryPort codigoVinculoRepository;

    @Mock
    private TokenGeneratorPort tokenGenerator;

    @InjectMocks
    private ValidarCodigoVinculoUseCase useCase;

    private static CodigoVinculo codigo(String valor, LocalDateTime expiraEm, boolean usado) {
        return CodigoVinculo.builder()
                .id(CODIGO_ID)
                .codigoUnico(valor)
                .cuidadorId(1L)
                .idosoId(IDOSO_ID)
                .expiraEm(expiraEm)
                .usado(usado)
                .build();
    }

    @Test
    void codigoValidoDeveGerarTokenComRoleIdoso() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("123456"))
                .thenReturn(Optional.of(codigo("123456", LocalDateTime.now().plusHours(1), false)));
        when(codigoVinculoRepository.marcarComoUsado(eq(CODIGO_ID), any(LocalDateTime.class))).thenReturn(true);
        when(tokenGenerator.generateToken(IDOSO_ID, Role.IDOSO)).thenReturn("jwt-idoso");

        String token = useCase.executar(" 123456 ");

        assertThat(token).isEqualTo("jwt-idoso");
        verify(tokenGenerator).generateToken(IDOSO_ID, Role.IDOSO);
    }

    @Test
    void codigoInexistenteDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar("000000")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void codigoExpiradoDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("111111"))
                .thenReturn(Optional.of(codigo("111111", LocalDateTime.now().minusMinutes(1), false)));

        assertThatThrownBy(() -> useCase.executar("111111")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(codigoVinculoRepository, never()).marcarComoUsado(anyLong(), any());
        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void codigoJaUsadoDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("222222"))
                .thenReturn(Optional.of(codigo("222222", LocalDateTime.now().plusHours(1), true)));

        assertThatThrownBy(() -> useCase.executar("222222")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(codigoVinculoRepository, never()).marcarComoUsado(anyLong(), any());
        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void codigoConsumidoPorRequisicaoConcorrenteDeveSerRejeitado() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("333333"))
                .thenReturn(Optional.of(codigo("333333", LocalDateTime.now().plusHours(1), false)));
        when(codigoVinculoRepository.marcarComoUsado(eq(CODIGO_ID), any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar("333333")).isInstanceOf(CodigoVinculoInvalidoException.class);

        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void mensagemDeErroDeveSerGenericaEIgualNosTresCasosDeFalha() {
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("000000")).thenReturn(Optional.empty());
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("111111"))
                .thenReturn(Optional.of(codigo("111111", LocalDateTime.now().minusMinutes(1), false)));
        when(codigoVinculoRepository.findMaisRecenteByCodigoUnico("222222"))
                .thenReturn(Optional.of(codigo("222222", LocalDateTime.now().plusHours(1), true)));

        String inexistente = catchThrowable(() -> useCase.executar("000000")).getMessage();
        String expirado = catchThrowable(() -> useCase.executar("111111")).getMessage();
        String usado = catchThrowable(() -> useCase.executar("222222")).getMessage();

        assertThat(inexistente).isEqualTo(expirado).isEqualTo(usado);
        assertThat(inexistente).doesNotContainIgnoringCase("usado")
                .doesNotContainIgnoringCase("não existe")
                .doesNotContainIgnoringCase("inexistente");
    }
}
