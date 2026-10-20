package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.configuracao.ConfiguracaoAlertaRepositoryPort;
import com.medcare.senior.domain.configuracao.ToleranciaInvalidaException;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ToleranciaUseCasesTest {

    private static final Long CUIDADOR_ID = 1L;

    @Mock
    private ConfiguracaoAlertaRepositoryPort configuracoes;

    private BuscarToleranciaUseCase buscar;
    private AtualizarToleranciaUseCase atualizar;

    @BeforeEach
    void setUp() {
        PoliticaDeJanela politica = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30),
                Duration.ofMinutes(180));
        buscar = new BuscarToleranciaUseCase(configuracoes, politica);
        atualizar = new AtualizarToleranciaUseCase(configuracoes);
    }

    @Test
    void semConfiguracaoPropriaValeAPadrao() {
        when(configuracoes.findToleranciaMinutos(CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThat(buscar.executar(CUIDADOR_ID)).isEqualTo(30);
    }

    @Test
    void comConfiguracaoPropriaValeADoCuidador() {
        when(configuracoes.findToleranciaMinutos(CUIDADOR_ID)).thenReturn(Optional.of(45));

        assertThat(buscar.executar(CUIDADOR_ID)).isEqualTo(45);
    }

    @ParameterizedTest
    @ValueSource(ints = {5, 30, 120})
    void gravaDentroDosLimitesInclusivos(int minutos) {
        assertThat(atualizar.executar(CUIDADOR_ID, minutos)).isEqualTo(minutos);
        verify(configuracoes).salvarToleranciaMinutos(CUIDADOR_ID, minutos);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 121, 0, -5})
    void recusaForaDosLimitesSemGravar(int minutos) {
        assertThatThrownBy(() -> atualizar.executar(CUIDADOR_ID, minutos))
                .isInstanceOf(ToleranciaInvalidaException.class)
                .hasMessage("A tolerância deve ficar entre 5 e 120 minutos");
        verify(configuracoes, never()).salvarToleranciaMinutos(anyLong(), anyInt());
    }
}
