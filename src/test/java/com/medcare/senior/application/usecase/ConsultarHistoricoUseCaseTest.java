package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseAvaliada;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.dose.PoliticaDeJanela;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.dose.StatusExibicao;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.historico.FiltroHistorico;
import com.medcare.senior.domain.historico.Pagina;
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
class ConsultarHistoricoUseCaseTest {

    private static final Long CUIDADOR_ID = 1L;
    private static final Long IDOSO_ID = 10L;
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    // 23:30 em São Paulo (já é o dia seguinte em UTC): "hoje" é o dia no fuso da aplicação
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 18, 23, 30);
    private static final LocalDate HOJE = AGORA.toLocalDate();

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private IdosoRepositoryPort idosoRepository;

    private ConsultarHistoricoUseCase useCase;

    @BeforeEach
    void setUp() {
        PoliticaDeJanela politica = new PoliticaDeJanela(Duration.ofMinutes(60), Duration.ofMinutes(30), Duration.ofMinutes(180));
        useCase = new ConsultarHistoricoUseCase(doseRepository, idosoRepository, politica,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private static DoseDetalhada detalhe(long id, LocalDateTime horario, StatusDose status) {
        Dose dose = Dose.builder().id(id).tratamentoId(1000L).horarioProgramado(horario).status(status).build();
        return new DoseDetalhada(dose, IDOSO_ID, "José", 100L, "Losartana", "50mg", "COMPRIMIDO");
    }

    @Test
    void semFiltrosBuscaTodosOsIdososDoCuidadorAteHojeEAvaliaAsDoses() {
        FiltroHistorico esperado = new FiltroHistorico(null, null, null, HOJE, null, 0, 20);
        when(doseRepository.findHistorico(CUIDADOR_ID, esperado)).thenReturn(new Pagina<>(List.of(
                detalhe(2L, HOJE.minusDays(1).atTime(8, 0), StatusDose.PENDENTE),
                detalhe(1L, HOJE.minusDays(2).atTime(8, 0), StatusDose.TOMADA)), 0, 20, 2));

        Pagina<DoseAvaliada> pagina = useCase.executar(CUIDADOR_ID, null, null, null, null, null, null, null);

        assertThat(pagina.totalElementos()).isEqualTo(2);
        assertThat(pagina.conteudo()).extracting(DoseAvaliada::statusExibicao)
                .containsExactly(StatusExibicao.NAO_TOMADA, StatusExibicao.TOMADA);
        verifyNoInteractions(idosoRepository);
    }

    @Test
    void comIdosoDoCuidadorRepassaOsFiltros() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Idoso.builder().id(IDOSO_ID).cuidadorId(CUIDADOR_ID).build()));
        FiltroHistorico esperado = new FiltroHistorico(IDOSO_ID, 100L, HOJE.minusDays(7), HOJE.minusDays(1),
                StatusDose.NAO_TOMADA, 1, 5);
        when(doseRepository.findHistorico(CUIDADOR_ID, esperado)).thenReturn(new Pagina<>(List.of(), 1, 5, 0));

        Pagina<DoseAvaliada> pagina = useCase.executar(CUIDADOR_ID, IDOSO_ID, 100L, HOJE.minusDays(7),
                HOJE.minusDays(1), StatusDose.NAO_TOMADA, 1, 5);

        // RN-011: período sem registros não é erro
        assertThat(pagina.conteudo()).isEmpty();
        assertThat(pagina.totalPaginas()).isZero();
    }

    @Test
    void idosoDeOutroCuidadorEhNaoEncontrado() {
        when(idosoRepository.findByIdAndCuidadorId(IDOSO_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, IDOSO_ID, null, null, null, null, null, null))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(doseRepository);
    }

    @Test
    void periodoInvalidoEhRecusadoAntesDeConsultar() {
        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, null, null, HOJE, HOJE.minusDays(1), null, null, null))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(doseRepository, idosoRepository);
    }
}
