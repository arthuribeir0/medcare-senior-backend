package com.medcare.senior.infrastructure.relatorio;

import com.medcare.senior.domain.dose.ConfirmadoPor;
import com.medcare.senior.domain.dose.Dose;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.relatorio.RelatorioDeAdesao;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.medcare.senior.infrastructure.relatorio.TextoDoPdf.paginas;
import static com.medcare.senior.infrastructure.relatorio.TextoDoPdf.semEspacos;
import static com.medcare.senior.infrastructure.relatorio.TextoDoPdf.texto;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * PDF gerado de verdade e lido de volta com o extrator de texto do OpenPDF.
 */
class OpenPdfGeradorAdapterTest {

    private static final LocalDateTime GERADO_EM = LocalDateTime.of(2026, 10, 20, 9, 30);
    private final OpenPdfGeradorAdapter adapter = new OpenPdfGeradorAdapter();

    private static DoseDetalhada dose(long id, LocalDateTime horario, StatusDose status, LocalDateTime confirmacao,
                                      String medicamento) {
        // Confirmadas pelo paciente, como até agora; a linha confirmada pelo cuidador é montada à parte
        Dose dose = Dose.builder().id(id).tratamentoId(1L).horarioProgramado(horario).horaConfirmacao(confirmacao)
                .status(status).confirmadoPor(confirmacao == null ? null : ConfirmadoPor.PACIENTE).build();
        return new DoseDetalhada(dose, 10L, "Maria", 100L, medicamento, "50mg", "COMPRIMIDO");
    }

    private static RelatorioDeAdesao relatorio(TotaisDoDia totais) {
        return new RelatorioDeAdesao(10L, "José Ângelo Conceição", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10),
                GERADO_EM, totais, List.of(
                new RelatorioDeAdesao.AdesaoDoMedicamento(100L, "Losartana", "50mg", new TotaisDoDia(8, 6, 1, 1, 0, 87.5)),
                new RelatorioDeAdesao.AdesaoDoMedicamento(200L, "Metformina", "500mg", new TotaisDoDia(2, 0, 0, 0, 2, null))));
    }

    @Test
    void trazNomePeriodoGeracaoTaxaTotaisPorMedicamentoEAsLinhasComAcentos() throws Exception {
        LocalDateTime dia1 = LocalDateTime.of(2026, 10, 1, 8, 0);
        List<DoseDetalhada> doses = List.of(
                dose(1, dia1, StatusDose.CONFIRMADA_NO_HORARIO, dia1.plusMinutes(5), "Losartana"),
                dose(2, dia1.plusHours(12), StatusDose.CONFIRMADA_COM_ATRASO, dia1.plusHours(13), "Losartana"),
                dose(3, dia1.plusDays(1), StatusDose.NAO_TOMADA, null, "Metformina"),
                dose(4, dia1.plusDays(2).withHour(23).withMinute(30), StatusDose.CONFIRMADA_NO_HORARIO, dia1.plusDays(3).withHour(0).withMinute(10), "Losartana"),
                dose(5, dia1.plusDays(9), StatusDose.PENDENTE, null, "Metformina"),
                new DoseDetalhada(Dose.builder().id(6L).tratamentoId(1L).horarioProgramado(dia1.plusDays(9).withHour(20))
                        .horaConfirmacao(dia1.plusDays(9).withHour(20).withMinute(15)).status(StatusDose.CONFIRMADA_NO_HORARIO)
                        .confirmadoPor(ConfirmadoPor.CUIDADOR).build(), 10L, "Maria", 100L, "Losartana", "50mg", "COMPRIMIDO"));

        byte[] pdf = adapter.gerar(relatorio(new TotaisDoDia(10, 6, 1, 1, 2, 87.5)), lote -> lote.accept(doses));

        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        String texto = semEspacos(texto(pdf));
        for (String esperado : List.of("Histórico de adesão ao tratamento", "Paciente: José Ângelo Conceição",
                "Período: 01/10/2026 a 10/10/2026", "Gerado em: 20/10/2026 09:30", "Taxa de adesão: 87,5%",
                "10 6 1 1 2",
                "Adesão por medicamento", "Losartana 50mg 8 6 1 1 0 87,5%", "Metformina 500mg 2 0 0 0 2 sem doses encerradas",
                "Registro das doses",
                "01/10/2026 08:00 Losartana 50mg Confirmada no horário 08:05 pelo paciente",
                "01/10/2026 20:00 Losartana 50mg Confirmada com atraso 21:00 pelo paciente",
                "02/10/2026 08:00 Metformina 50mg Não confirmada —",
                // Confirmação no dia seguinte leva a data
                "03/10/2026 23:30 Losartana 50mg Confirmada no horário 04/10/2026 00:10 pelo paciente",
                "10/10/2026 08:00 Metformina 50mg Pendente",
                // Confirmada pelo cuidador na tela da dose
                "10/10/2026 20:00 Losartana 50mg Confirmada no horário 20:15 pelo cuidador",
                "Página 1")) {
            assertThat(texto).contains(semEspacos(esperado));
        }
    }

    @Test
    void centenasDeDosesEmVariosLotesViramVariasPaginasNaOrdem() throws Exception {
        LocalDateTime inicio = LocalDateTime.of(2026, 10, 1, 6, 0);
        byte[] pdf = adapter.gerar(relatorio(new TotaisDoDia(1200, 1200, 0, 0, 0, 100.0)), entrega -> {
            for (int lote = 0; lote < 3; lote++) {
                List<DoseDetalhada> doses = new ArrayList<>();
                for (int i = 0; i < 400; i++) {
                    int n = lote * 400 + i;
                    doses.add(dose(n, inicio.plusHours(n), StatusDose.CONFIRMADA_NO_HORARIO, inicio.plusHours(n).plusMinutes(1), "Losartana"));
                }
                entrega.accept(doses);
            }
        });

        int paginas = paginas(pdf);
        assertThat(paginas).isGreaterThan(10);
        String texto = semEspacos(texto(pdf));
        // Primeira e última dose, e o cabeçalho da tabela repetido nas páginas seguintes
        assertThat(texto).contains(semEspacos("01/10/2026 06:00 Losartana 50mg Confirmada no horário 06:01 pelo paciente"))
                .contains(semEspacos("19/11/2026 21:00 Losartana 50mg Confirmada no horário 21:01 pelo paciente"))
                .contains(semEspacos("Página " + paginas));
        assertThat(texto.indexOf("01/10/202606:00")).isLessThan(texto.indexOf("19/11/202621:00"));
        assertThat(texto.split("Horaprogramada", -1).length - 1).isGreaterThan(paginas - 2);
    }

    @Test
    void textosDeStatusConfirmacaoETaxa() {
        assertThat(OpenPdfGeradorAdapter.status(StatusDose.NAO_TOMADA)).isEqualTo("Não confirmada");
        assertThat(OpenPdfGeradorAdapter.taxa(66.7)).isEqualTo("66,7%");
        assertThat(OpenPdfGeradorAdapter.taxa(100.0)).isEqualTo("100,0%");
        assertThat(OpenPdfGeradorAdapter.taxa(null)).isEqualTo("sem doses encerradas");
        LocalDateTime programada = LocalDateTime.of(2026, 10, 1, 8, 0);
        assertThat(OpenPdfGeradorAdapter.confirmacao(programada, null, null)).isEqualTo("—");
        assertThat(OpenPdfGeradorAdapter.confirmacao(programada, programada.plusMinutes(3), ConfirmadoPor.PACIENTE))
                .isEqualTo("08:03 pelo paciente");
        assertThat(OpenPdfGeradorAdapter.confirmacao(programada, programada.plusMinutes(3), ConfirmadoPor.CUIDADOR))
                .isEqualTo("08:03 pelo cuidador");
        // Sem quem confirmou (não acontece com o CHECK da V15), só a hora
        assertThat(OpenPdfGeradorAdapter.confirmacao(programada, programada.plusMinutes(3), null)).isEqualTo("08:03");
    }
}
