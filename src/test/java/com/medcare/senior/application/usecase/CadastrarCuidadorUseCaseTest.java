package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.autenticacao.PasswordHasherPort;
import com.medcare.senior.domain.exception.ConsentimentoLgpdObrigatorioException;
import com.medcare.senior.domain.exception.EmailJaCadastradoException;
import com.medcare.senior.domain.exception.SenhaFracaException;
import com.medcare.senior.domain.usuario.Cuidador;
import com.medcare.senior.domain.usuario.CuidadorRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CadastrarCuidadorUseCaseTest {

    private static final String SENHA_FORTE = "SenhaForte1";
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 16, 9, 30);

    @Mock
    private CuidadorRepositoryPort cuidadorRepository;

    @Mock
    private PasswordHasherPort passwordHasher;

    private CadastrarCuidadorUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CadastrarCuidadorUseCase(cuidadorRepository, passwordHasher,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    @Test
    void deveCadastrarCuidadorComSenhaHasheada() {
        when(passwordHasher.hash(SENHA_FORTE)).thenReturn("hash-bcrypt");
        Cuidador salvo = Cuidador.builder().id(1L).nome("Ana").email("ana@email.com").senhaHash("hash-bcrypt").build();
        when(cuidadorRepository.save(any(Cuidador.class))).thenReturn(salvo);

        Cuidador resultado = useCase.executar("  Ana  ", "ana@email.com", SENHA_FORTE, "11999999999", true);

        assertThat(resultado).isSameAs(salvo);

        ArgumentCaptor<Cuidador> captor = ArgumentCaptor.forClass(Cuidador.class);
        InOrder ordem = inOrder(passwordHasher, cuidadorRepository);
        ordem.verify(passwordHasher).hash(SENHA_FORTE);
        ordem.verify(cuidadorRepository).save(captor.capture());

        Cuidador enviado = captor.getValue();
        assertThat(enviado.getSenhaHash()).isEqualTo("hash-bcrypt").isNotEqualTo(SENHA_FORTE);
        assertThat(enviado.getNome()).isEqualTo("Ana");
        assertThat(enviado.getEmail()).isEqualTo("ana@email.com");
        assertThat(enviado.getTelefone()).isEqualTo("11999999999");
        assertThat(enviado.getDataCadastro()).isEqualTo(AGORA);
        assertThat(enviado.isConsentimentoLgpd()).isTrue();
        assertThat(enviado.getDataConsentimento()).isEqualTo(enviado.getDataCadastro());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = false)
    void deveRejeitarCadastroSemConsentimentoLgpd(Boolean consentimento) {
        assertThatThrownBy(() -> useCase.executar("Ana", "ana@email.com", SENHA_FORTE, null, consentimento))
                .isInstanceOf(ConsentimentoLgpdObrigatorioException.class)
                .hasMessageContaining("termo de consentimento");

        verifyNoInteractions(cuidadorRepository, passwordHasher);
    }

    @Test
    void deveRejeitarEmailJaCadastrado() {
        when(cuidadorRepository.existsByEmail("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar("Ana", "ana@email.com", SENHA_FORTE, null, true))
                .isInstanceOf(EmailJaCadastradoException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(cuidadorRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "senhafraca1", // sem maiúscula
            "SenhaFraca",  // sem número
            "Senha1",      // menos de 8 caracteres
            "SENHAFRACA1"  // sem minúscula
    })
    void deveRejeitarSenhaFraca(String senha) {
        assertThatThrownBy(() -> useCase.executar("Ana", "ana@email.com", senha, null, true))
                .isInstanceOf(SenhaFracaException.class);

        verify(passwordHasher, never()).hash(anyString());
        verify(cuidadorRepository, never()).save(any());
    }

    @Test
    void deveNormalizarEmailAntesDeChecarDuplicidade() {
        when(cuidadorRepository.existsByEmail("ana@email.com")).thenReturn(true);

        assertThatThrownBy(() -> useCase.executar("Ana", "  Ana@EMAIL.com  ", SENHA_FORTE, null, true))
                .isInstanceOf(EmailJaCadastradoException.class);

        verify(cuidadorRepository).existsByEmail("ana@email.com");
    }

    @Test
    void deveSalvarEmailNormalizado() {
        when(passwordHasher.hash(SENHA_FORTE)).thenReturn("hash-bcrypt");
        when(cuidadorRepository.save(any(Cuidador.class))).thenAnswer(invocacao -> invocacao.getArgument(0));

        Cuidador resultado = useCase.executar("Ana", "  Ana@EMAIL.com  ", SENHA_FORTE, null, true);

        assertThat(resultado.getEmail()).isEqualTo("ana@email.com");
    }
}
