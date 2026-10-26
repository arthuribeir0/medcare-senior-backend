package com.medcare.senior.application.usecase;

import com.medcare.senior.domain.dose.DoseRepositoryPort;
import com.medcare.senior.domain.exception.ParametroInvalidoException;
import com.medcare.senior.domain.exception.PeriodoSemRegistrosException;
import com.medcare.senior.domain.exception.RecursoNaoEncontradoException;
import com.medcare.senior.domain.relatorio.ContagemDeDoses;
import com.medcare.senior.domain.relatorio.GeradorDePdfPort;
import com.medcare.senior.domain.relatorio.RelatorioDeAdesao;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import com.medcare.senior.domain.usuario.Paciente;
import com.medcare.senior.domain.usuario.PacienteRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static com.medcare.senior.domain.dose.StatusDose.CONFIRMADA_COM_ATRASO;
import static com.medcare.senior.domain.dose.StatusDose.NAO_TOMADA;
import static com.medcare.senior.domain.dose.StatusDose.PENDENTE;
import static com.medcare.senior.domain.dose.StatusDose.CONFIRMADA_NO_HORARIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GerarRelatorioPdfUseCaseTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime AGORA = LocalDateTime.of(2026, 10, 20, 9, 30);
    private static final Long CUIDADOR_ID = 1L;
    private static final Long PACIENTE_ID = 10L;
    private static final LocalDate DE = LocalDate.of(2026, 10, 1);

    @Mock
    private PacienteRepositoryPort pacienteRepository;

    @Mock
    private DoseRepositoryPort doseRepository;

    @Mock
    private GeradorDePdfPort gerador;

    private GerarRelatorioPdfUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GerarRelatorioPdfUseCase(pacienteRepository, doseRepository, gerador,
                Clock.fixed(AGORA.atZone(FUSO).toInstant(), FUSO));
    }

    private void pacienteDoCuidador() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID))
                .thenReturn(Optional.of(Paciente.builder().id(PACIENTE_ID).cuidadorId(CUIDADOR_ID).nome("Maria da Silva").build()));
    }

    @Test
    void montaTotaisDoPeriodoEPorMedicamentoEmOrdemDeNomeEPassaAsDosesEmLotes() {
        pacienteDoCuidador();
        when(doseRepository.contarPorMedicamentoEStatus(PACIENTE_ID, DE.atStartOfDay(), LocalDate.of(2026, 10, 11).atStartOfDay()))
                .thenReturn(List.of(
                        new ContagemDeDoses(200L, "metformina", "500mg", CONFIRMADA_NO_HORARIO, 3),
                        new ContagemDeDoses(100L, "Losartana", "50mg", CONFIRMADA_NO_HORARIO, 6),
                        new ContagemDeDoses(100L, "Losartana", "50mg", CONFIRMADA_COM_ATRASO, 1),
                        new ContagemDeDoses(100L, "Losartana", "50mg", NAO_TOMADA, 1),
                        new ContagemDeDoses(200L, "metformina", "500mg", PENDENTE, 2)));
        when(gerador.gerar(any(), any())).thenReturn(new byte[]{1, 2, 3});

        GerarRelatorioPdfUseCase.Resultado resultado = useCase.executar(CUIDADOR_ID, PACIENTE_ID, DE, LocalDate.of(2026, 10, 10));

        RelatorioDeAdesao relatorio = resultado.relatorio();
        assertThat(resultado.pdf()).containsExactly(1, 2, 3);
        assertThat(relatorio.pacienteNome()).isEqualTo("Maria da Silva");
        assertThat(relatorio.de()).isEqualTo(DE);
        assertThat(relatorio.ate()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(relatorio.geradoEm()).isEqualTo(AGORA);
        // (9 + 1) / (13 - 2) = 90,9%
        assertThat(relatorio.totais()).isEqualTo(new TotaisDoDia(13, 9, 1, 1, 2, 90.9));
        assertThat(relatorio.porMedicamento()).extracting(RelatorioDeAdesao.AdesaoDoMedicamento::nome)
                .containsExactly("Losartana", "metformina");
        assertThat(relatorio.porMedicamento().get(0).totais()).isEqualTo(new TotaisDoDia(8, 6, 1, 1, 0, 87.5));
        assertThat(relatorio.porMedicamento().get(1).totais()).isEqualTo(new TotaisDoDia(5, 3, 0, 0, 2, 100.0));

        // A fonte de doses lê em lotes no mesmo intervalo
        ArgumentCaptor<GeradorDePdfPort.FonteDeDoses> fonte = ArgumentCaptor.forClass(GeradorDePdfPort.FonteDeDoses.class);
        verify(gerador).gerar(eq(relatorio), fonte.capture());
        fonte.getValue().percorrer(lote -> { });
        verify(doseRepository).percorrerDoPaciente(eq(PACIENTE_ID), eq(DE.atStartOfDay()), eq(LocalDate.of(2026, 10, 11).atStartOfDay()),
                eq(GerarRelatorioPdfUseCase.TAMANHO_DO_LOTE), any());
    }

    @Test
    void semDataDeFimValeHoje() {
        pacienteDoCuidador();
        when(doseRepository.contarPorMedicamentoEStatus(eq(PACIENTE_ID), any(), eq(LocalDate.of(2026, 10, 21).atStartOfDay())))
                .thenReturn(List.of(new ContagemDeDoses(100L, "Losartana", "50mg", CONFIRMADA_NO_HORARIO, 1)));
        when(gerador.gerar(any(), any())).thenReturn(new byte[0]);

        assertThat(useCase.executar(CUIDADOR_ID, PACIENTE_ID, DE, null).relatorio().ate()).isEqualTo(AGORA.toLocalDate());
    }

    @Test
    void inicioAusenteOuDepoisDoFimEhRecusadoAntesDeConsultar() {
        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, PACIENTE_ID, null, null))
                .isInstanceOf(ParametroInvalidoException.class).extracting("campo").isEqualTo("de");
        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, PACIENTE_ID, DE, DE.minusDays(1)))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("A data de início não pode ser posterior à data de fim");
        verifyNoInteractions(pacienteRepository, doseRepository, gerador);
    }

    @Test
    void periodoDeExatamente12MesesEhAceito() {
        LocalDate de = LocalDate.of(2025, 10, 10);
        LocalDate ate = LocalDate.of(2026, 10, 10);
        pacienteDoCuidador();
        when(doseRepository.contarPorMedicamentoEStatus(eq(PACIENTE_ID), any(), any()))
                .thenReturn(List.of(new ContagemDeDoses(100L, "Losartana", "50mg", CONFIRMADA_NO_HORARIO, 1)));
        when(gerador.gerar(any(), any())).thenReturn(new byte[0]);

        assertThat(useCase.executar(CUIDADOR_ID, PACIENTE_ID, de, ate).relatorio().ate()).isEqualTo(ate);
    }

    @Test
    void periodoDe12MesesMaisUmDiaEhRecusadoAntesDeConsultar() {
        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, PACIENTE_ID, LocalDate.of(2025, 10, 10), LocalDate.of(2026, 10, 11)))
                .isInstanceOf(ParametroInvalidoException.class)
                .hasMessage("O período do relatório pode ter no máximo 12 meses. Escolha uma data de fim até 10/10/2026.")
                .extracting("campo").isEqualTo("ate");
        // Sem data de fim vale hoje (20/10/2026): início há mais de 12 meses também é recusado
        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, PACIENTE_ID, LocalDate.of(2025, 10, 19), null))
                .isInstanceOf(ParametroInvalidoException.class);
        verifyNoInteractions(pacienteRepository, doseRepository, gerador);
    }

    @Test
    void pacienteDeOutroCuidadorEhNaoEncontrado() {
        when(pacienteRepository.findByIdAndCuidadorId(PACIENTE_ID, CUIDADOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, PACIENTE_ID, DE, DE)).isInstanceOf(RecursoNaoEncontradoException.class);
        verifyNoInteractions(doseRepository, gerador);
    }

    @Test
    void periodoSemDosesNaoGeraArquivo() {
        pacienteDoCuidador();
        when(doseRepository.contarPorMedicamentoEStatus(eq(PACIENTE_ID), any(), any())).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.executar(CUIDADOR_ID, PACIENTE_ID, DE, DE)).isInstanceOf(PeriodoSemRegistrosException.class);
        verifyNoInteractions(gerador);
    }

    @Test
    void resultadoComparaOConteudoDoPdfENaoExpoeDadosNoToString() {
        RelatorioDeAdesao relatorio = new RelatorioDeAdesao(PACIENTE_ID, "José Souza", DE, DE, LocalDateTime.of(2026, 10, 8, 9, 0),
                TotaisDoDia.deContagens(1, 0, 0, 0), List.of());
        GerarRelatorioPdfUseCase.Resultado resultado = new GerarRelatorioPdfUseCase.Resultado(relatorio, new byte[]{1, 2, 3});

        assertThat(resultado).isEqualTo(new GerarRelatorioPdfUseCase.Resultado(relatorio, new byte[]{1, 2, 3}))
                .hasSameHashCodeAs(new GerarRelatorioPdfUseCase.Resultado(relatorio, new byte[]{1, 2, 3}))
                .isNotEqualTo(new GerarRelatorioPdfUseCase.Resultado(relatorio, new byte[]{9}))
                .isNotEqualTo(new GerarRelatorioPdfUseCase.Resultado(null, new byte[]{1, 2, 3}))
                .isNotEqualTo("outro tipo");
        assertThat(resultado.toString()).isEqualTo("Resultado[pacienteId=" + PACIENTE_ID + ", pdf=3 bytes]")
                .doesNotContain("José");
        assertThat(new GerarRelatorioPdfUseCase.Resultado(null, null)).hasToString("Resultado[pacienteId=null, pdf=null]");
    }
}
