package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.dose.StatusExibicao;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.relatorio.RelatorioDiario;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
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
class GerarRelatorioDiarioUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // 23:30 em São Paulo (já é o dia seguinte em UTC): o dia padrão tem de ser o do fuso da aplicação
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 8, 23, 30);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock
    private IdosoRepositoryPort idosoRepository;

    @Mock
    private DoseRepositoryPort doseRepository;

    private GerarRelatorioDiarioUseCase useCase;

    @BeforeEach
    void setUp() {
        PoliticaDeJanela politica = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(180));
        useCase = new GerarRelatorioDiarioUseCase(idosoRepository, doseRepository, politica,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private void idosoDoCuidador() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).nome("José").cuidadorId(CUIDADOR_ID).build()));
    }

    private static DoseDetalhada detalhe(long id, LocalDateTime horario, StatusDose status) {
        Dose dose = Dose.builder().id(id).tratamentoId(1000L).horarioProgramado(horario).status(status).build();
        return new DoseDetalhada(dose, IDOSO_ID, "José", 100L, "Losartana", "50mg", "COMPRIMIDO");
    }

    @Test
    void semDataUsaHojeNoFusoDaAplicacaoEMontaTotaisEDoses() {
        idosoDoCuidador();
        when(doseRepository.findDetalhadasDoIdosoEntre(IDOSO_ID, HOJE.atStartOfDay(), HOJE.plusDays(1).atStartOfDay()))
                .thenReturn(List.of(
                        detalhe(1L, HOJE.atTime(8, 0), StatusDose.CONFIRMADA_NO_HORARIO),
                        detalhe(2L, HOJE.atTime(12, 0), StatusDose.NAO_TOMADA),
                        detalhe(3L, HOJE.atTime(22, 0), StatusDose.PENDENTE)));

        RelatorioDiario relatorio = useCase.executar(IDOSO_ID, CUIDADOR_ID, null);

        assertThat(relatorio.data()).isEqualTo(HOJE);
        assertThat(relatorio.idosoId()).isEqualTo(IDOSO_ID);
        assertThat(relatorio.idosoNome()).isEqualTo("José");
        assertThat(relatorio.totais()).isEqualTo(new TotaisDoDia(3, 1, 0, 1, 1, 50.0));
        assertThat(relatorio.doses()).extracting(d -> d.detalhe().dose().getId()).containsExactly(1L, 2L, 3L);
        // As doses vêm avaliadas no instante atual: a das 22:00 ainda PENDENTE já aparece ATRASADA
        assertThat(relatorio.doses().get(2).statusExibicao()).isEqualTo(StatusExibicao.ATRASADA);
    }

    @Test
    void diaSemDosesDevolveRelatorioVazioSemAderencia() {
        LocalDate dia = HOJE.minusDays(3);
        idosoDoCuidador();
        when(doseRepository.findDetalhadasDoIdosoEntre(IDOSO_ID, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()))
                .thenReturn(List.of());

        RelatorioDiario relatorio = useCase.executar(IDOSO_ID, CUIDADOR_ID, dia);

        assertThat(relatorio.data()).isEqualTo(dia);
        assertThat(relatorio.doses()).isEmpty();
        assertThat(relatorio.totais()).isEqualTo(new TotaisDoDia(0, 0, 0, 0, 0, null));
    }

    @Test
    void idosoDeOutroCuidadorEhNaoEncontrado() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(IDOSO_ID, CUIDADOR_ID, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(doseRepository);
    }
}
