package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import com.medcare.senior.domain.autenticacao.SessaoAutenticada;
import com.medcare.senior.domain.autenticacao.TokenGeneratorPort;
import com.medcare.senior.domain.exception.CredenciaisInvalidasException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import com.medcare.senior.domain.usuario.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutenticarCuidadorUseCaseTest {

    private static final String HASH_FICTICIO = "hash-ficticio";

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    @Mock
    private TokenGeneratorPort tokenGenerator;

    private AutenticarCuidadorUseCase useCase;

    private final Cuidador cuidador = Cuidador.builder()
            .id(7L)
            .nome("Ana Souza")
            .email("ana@email.com")
            .senhaHash("hash-real")
            .build();

    @BeforeEach
    void setUp() {
        // O construtor gera o hash fictício usado quando o e-mail não existe
        when(passwordHasher.hash(anyString())).thenReturn(HASH_FICTICIO);
        useCase = new AutenticarCuidadorUseCase(cuidadorRepository, passwordHasher, tokenGenerator);
    }

    @Test
    void deveRetornarTokenIdENomeQuandoCredenciaisCorretas() {
        when(cuidadorRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(cuidador));
        when(passwordHasher.matches("SenhaForte1", "hash-real")).thenReturn(true);
        when(tokenGenerator.generateToken(7L, Role.CUIDADOR)).thenReturn("jwt-token");

        SessaoAutenticada sessao = useCase.executar("  Ana@Email.com ", "SenhaForte1");

        assertThat(sessao).isEqualTo(new SessaoAutenticada("jwt-token", 7L, "Ana Souza"));
    }

    @Test
    void deveRejeitarEmailInexistente() {
        when(cuidadorRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar("naoexiste@email.com", "SenhaForte1"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        // Mesmo sem cuidador, a senha é comparada com o hash fictício (tempo de resposta constante)
        verify(passwordHasher).matches("SenhaForte1", HASH_FICTICIO);
        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void deveRejeitarSenhaIncorreta() {
        when(cuidadorRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(cuidador));
        when(passwordHasher.matches("SenhaErrada1", "hash-real")).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar("ana@email.com", "SenhaErrada1"))
                .isInstanceOf(CredenciaisInvalidasException.class);

        verify(tokenGenerator, never()).generateToken(anyLong(), any());
    }

    @Test
    void mensagemDeErroDeveSerIgualParaEmailInexistenteESenhaIncorreta() {
        when(cuidadorRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());
        when(cuidadorRepository.findByEmail("ana@email.com")).thenReturn(Optional.of(cuidador));
        when(passwordHasher.matches(anyString(), anyString())).thenReturn(false);

        Throwable emailInexistente = catchThrowable(() -> useCase.executar("naoexiste@email.com", "SenhaErrada1"));
        Throwable senhaIncorreta = catchThrowable(() -> useCase.executar("ana@email.com", "SenhaErrada1"));

        assertThat(emailInexistente).isInstanceOf(CredenciaisInvalidasException.class);
        assertThat(senhaIncorreta).isInstanceOf(CredenciaisInvalidasException.class);
        assertThat(emailInexistente.getMessage()).isEqualTo(senhaIncorreta.getMessage());
        assertThat(emailInexistente.getMessage()).doesNotContainIgnoringCase("naoexiste");
    }
}
