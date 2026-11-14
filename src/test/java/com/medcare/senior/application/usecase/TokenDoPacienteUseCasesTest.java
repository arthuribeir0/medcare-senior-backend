/*
 * Testes automatizados de token do paciente (5 casos). Cobre: grava o token sem espacos com o instante e retira
 * dos outros pacientes; recusa token vazio; recusa token maior que a coluna; paciente que não existe mais é sessão
 * inválida e não mexe em outros pacientes; remover apaga só o token do paciente do jwt.
 */
package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.exception.TokenDispositivoInvalidoException;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenDoPacienteUseCasesTest {

    private static final Long PACIENTE_ID = 20L;
    private static final Instant AGORA = Instant.parse("2026-10-16T11:00:00Z");

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    private RegistrarTokenDoPacienteUseCase registrar;
    private RemoverTokenDoPacienteUseCase remover;

    @BeforeEach
    void setUp() {
        registrar = new RegistrarTokenDoPacienteUseCase(pacienteRepository, Clock.fixed(AGORA, ZoneOffset.UTC));
        remover = new RemoverTokenDoPacienteUseCase(pacienteRepository);
    }

    @Test
    void gravaOTokenSemEspacosComOInstanteERetiraDosOutrosPacientes() {
        when(pacienteRepository.atualizarTokenFcm(PACIENTE_ID, "token-fcm", AGORA)).thenReturn(true);

        registrar.executar(PACIENTE_ID, "  token-fcm  ");

        InOrder ordem = inOrder(pacienteRepository);
        ordem.verify(pacienteRepository).atualizarTokenFcm(PACIENTE_ID, "token-fcm", AGORA);
        ordem.verify(pacienteRepository).removerTokenFcmDosOutrosPacientes("token-fcm", PACIENTE_ID);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void recusaTokenVazio(String token) {
        assertThatThrownBy(() -> registrar.executar(PACIENTE_ID, token)).isInstanceOf(TokenDispositivoInvalidoException.class);
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void recusaTokenMaiorQueAColuna() {
        String grande = "a".repeat(RegistrarTokenDoPacienteUseCase.TAMANHO_MAXIMO + 1);

        assertThatThrownBy(() -> registrar.executar(PACIENTE_ID, grande)).isInstanceOf(TokenDispositivoInvalidoException.class);
        verifyNoInteractions(pacienteRepository);
    }

    @Test
    void pacienteQueNaoExisteMaisEhSessaoInvalidaENaoMexeEmOutrosPacientes() {
        when(pacienteRepository.atualizarTokenFcm(PACIENTE_ID, "token-fcm", AGORA)).thenReturn(false);

        assertThatThrownBy(() -> registrar.executar(PACIENTE_ID, "token-fcm")).isInstanceOf(SessaoInvalidaException.class);
        verify(pacienteRepository, never()).removerTokenFcmDosOutrosPacientes(anyString(), anyLong());
    }

    @Test
    void removerApagaSoOTokenDoPacienteDoJwt() {
        remover.executar(PACIENTE_ID);

        verify(pacienteRepository).removerTokenFcm(PACIENTE_ID);
    }
}
