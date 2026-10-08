package com.medcare.senior.application.usecase;

import com.medcare.senior.application.service.GeradorDeDosesService;
import com.medcare.senior.domain.tratamento.Tratamento;
import com.medcare.senior.domain.tratamento.TratamentoRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReabastecerDosesUseCaseTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // 00:05, horário padrão do job
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 9, 0, 5);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock
    private TratamentoRepositoryPort tratamentoRepository;

    @Mock
    private GeradorDeDosesService geradorDeDoses;

    private ReabastecerDosesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReabastecerDosesUseCase(tratamentoRepository, geradorDeDoses,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static Tratamento tratamento(long id) {
        return Tratamento.builder().id(id).build();
    }

    @Test
    void deveGerarDosesParaCadaTratamentoAtivoDeHoje() {
        Tratamento t1 = tratamento(1L);
        Tratamento t2 = tratamento(2L);
        when(tratamentoRepository.findAllAtivos(HOJE)).thenReturn(List.of(t1, t2));
        when(geradorDeDoses.gerar(t1)).thenReturn(2);
        when(geradorDeDoses.gerar(t2)).thenReturn(1);

        ReabastecerDosesUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new ReabastecerDosesUseCase.Resultado(2, 3, 0));
        // "Hoje" vem do Clock (fuso app.timezone), não do relógio do servidor
        verify(tratamentoRepository).findAllAtivos(HOJE);
    }

    @Test
    void erroEmUmTratamentoNaoImpedeOsDemais() {
        Tratamento comErro = tratamento(1L);
        Tratamento depois = tratamento(2L);
        when(tratamentoRepository.findAllAtivos(HOJE)).thenReturn(List.of(comErro, depois));
        when(geradorDeDoses.gerar(comErro)).thenThrow(new IllegalStateException("falha simulada"));
        when(geradorDeDoses.gerar(depois)).thenReturn(4);

        ReabastecerDosesUseCase.Resultado resultado = useCase.executar();

        assertThat(resultado).isEqualTo(new ReabastecerDosesUseCase.Resultado(2, 4, 1));
        verify(geradorDeDoses).gerar(depois);
    }

    @Test
    void semTratamentosAtivosNaoGeraNada() {
        when(tratamentoRepository.findAllAtivos(HOJE)).thenReturn(List.of());

        assertThat(useCase.executar()).isEqualTo(new ReabastecerDosesUseCase.Resultado(0, 0, 0));

        verifyNoInteractions(geradorDeDoses);
    }
}
