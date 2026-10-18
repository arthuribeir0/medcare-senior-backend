package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Confirmabilidade;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.dose.StatusExibicao;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.exception.SessaoInvalidaException;
import com.medcare.senior.domain.usuario.Idoso;
import com.medcare.senior.domain.usuario.IdosoRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListarDosesDoDiaUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // 23:30 em São Paulo (já é o dia seguinte em UTC): "hoje" tem de ser o dia no fuso da aplicação
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 23, 30);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    private ListarDosesDoDiaUseCase useCase;

    @BeforeEach
    void setUp() {
        PoliticaDeJanela politica = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(180));
        useCase = new ListarDosesDoDiaUseCase(doseRepository, idosoRepository, politica,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseDetalhada detalhe(long id, LocalDateTime horario, StatusDose status) {
        Dose dose = Dose.builder().id(id).tratamentoId(1000L).horarioProgramado(horario).status(status).build();
        return new DoseDetalhada(dose, IDOSO_ID, "José", 100L, "Losartana", "50mg", "COMPRIMIDO");
    }

    @Test
    void idosoSemDataListaAsDosesDeHojeNoFusoDaAplicacaoComAvaliacao() {
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).build()));
        when(doseRepository.findDetalhadasDoIdosoEntre(IDOSO_ID, HOJE.atStartOfDay(), HOJE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        detalhe(1L, HOJE.atTime(8, 0), StatusDose.TOMADA),
                        detalhe(2L, HOJE.atTime(22, 0), StatusDose.PENDENTE),
                        detalhe(3L, HOJE.atTime(23, 45), StatusDose.PENDENTE)));

        List<DoseAvaliada> doses = useCase.executarComoIdoso(IDOSO_ID, null);

        assertThat(doses).extracting(d -> d.detalhe().dose().getId()).containsExactly(1L, 2L, 3L);
        assertThat(doses).extracting(DoseAvaliada::statusExibicao)
                .containsExactly(StatusExibicao.TOMADA, StatusExibicao.ATRASADA, StatusExibicao.PENDENTE);
        assertThat(doses).extracting(DoseAvaliada::confirmabilidade)
                .containsExactly(Confirmabilidade.JA_REGISTRADA, Confirmabilidade.OK, Confirmabilidade.OK);
    }

    @Test
    void idosoComDataInformadaUsaODiaPedido() {
        LocalDate ontem = HOJE.minusDays(1);
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).build()));
        when(doseRepository.findDetalhadasDoIdosoEntre(IDOSO_ID, ontem.atStartOfDay(), HOJE.atStartOfDay()))
                .thenReturn(List.of(detalhe(1L, ontem.atTime(8, 0), StatusDose.PENDENTE)));

        // Dose de ontem às 08:00 ainda PENDENTE no banco (job ainda não passou): exibida como NAO_TOMADA
        assertThat(useCase.executarComoIdoso(IDOSO_ID, ontem)).singleElement()
                .satisfies(d -> {
                    assertThat(d.statusExibicao()).isEqualTo(StatusExibicao.NAO_TOMADA);
                    assertThat(d.confirmabilidade()).isEqualTo(Confirmabilidade.ENCERRADA);
                });
    }

    @Test
    void idosoQueNaoExisteMaisTemSessaoInvalida() {
        when(idosoRepository.findById(IDOSO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executarComoIdoso(IDOSO_ID, null)).isInstanceOf(SessaoInvalidaException.class);
        verifyNoInteractions(doseRepository);
    }

    @Test
    void cuidadorListaAsDosesDeUmIdosoDele() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).cuidadorId(CUIDADOR_ID).build()));
        when(doseRepository.findDetalhadasDoIdosoEntre(IDOSO_ID, HOJE.atStartOfDay(), HOJE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(detalhe(1L, HOJE.atTime(8, 0), StatusDose.TOMADA)));

        assertThat(useCase.executarComoCuidador(IDOSO_ID, CUIDADOR_ID, HOJE)).hasSize(1);
    }

    @Test
    void cuidadorNaoVeDosesDeIdosoDeOutroCuidador() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executarComoCuidador(IDOSO_ID, CUIDADOR_ID, null))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessage("Idoso não encontrado");
        verifyNoInteractions(doseRepository);
    }
}
