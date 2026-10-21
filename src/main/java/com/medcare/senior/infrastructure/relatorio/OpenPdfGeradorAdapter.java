package com.medcare.senior.infrastructure.relatorio;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.medcare.senior.domain.dose.DoseDetalhada;
import com.medcare.senior.domain.dose.StatusDose;
import com.medcare.senior.domain.relatorio.GeradorDePdfPort;
import com.medcare.senior.domain.relatorio.RelatorioDeAdesao;
import com.medcare.senior.domain.relatorio.TotaisDoDia;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * PDF do histórico de adesão com OpenPDF (UC-012, RN-012). A tabela de doses é enviada ao documento a cada lote
 * (modo de tabela grande do OpenPDF): só o arquivo em bytes cresce, não a árvore de objetos. Fontes padrão do PDF
 * (Helvetica, codificação WinAnsi), que cobrem os acentos do português. Nada além do idoso pedido entra no arquivo.
 */
@Component
public class OpenPdfGeradorAdapter implements GeradorDePdfPort {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color CINZA = new Color(0xEE, 0xF1, 0xF5);
    private static final Font TITULO = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font SUBTITULO = new Font(Font.HELVETICA, 12, Font.BOLD);
    private static final Font TEXTO = new Font(Font.HELVETICA, 10);
    private static final Font TEXTO_NEGRITO = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font TABELA = new Font(Font.HELVETICA, 9);
    private static final Font TABELA_CABECALHO = new Font(Font.HELVETICA, 9, Font.BOLD);
    private static final Font RODAPE = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.GRAY);

    @Override
    public byte[] gerar(RelatorioDeAdesao relatorio, FonteDeDoses doses) {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 36, 36, 40, 48);
        PdfWriter writer = PdfWriter.getInstance(documento, saida);
        writer.setPageEvent(new Rodape(relatorio.geradoEm()));
        documento.addTitle("Histórico de adesão ao tratamento");
        documento.addCreator("MedCare Sênior");
        documento.open();

        cabecalho(documento, relatorio);
        resumo(documento, relatorio.totais());
        porMedicamento(documento, relatorio);
        tabelaDeDoses(documento, doses);

        documento.close();
        return saida.toByteArray();
    }

    private static void cabecalho(Document documento, RelatorioDeAdesao relatorio) {
        documento.add(new Paragraph("Histórico de adesão ao tratamento", TITULO));
        documento.add(linha("Idoso: ", relatorio.idosoNome()));
        documento.add(linha("Período: ", relatorio.de().format(DATA) + " a " + relatorio.ate().format(DATA)));
        documento.add(linha("Gerado em: ", relatorio.geradoEm().format(DATA_HORA) + " (horário de Brasília)"));
        documento.add(Chunk.NEWLINE);
    }

    private static void resumo(Document documento, TotaisDoDia totais) {
        documento.add(new Paragraph("Resumo do período", SUBTITULO));
        documento.add(linha("Taxa de adesão: ", taxa(totais.aderenciaPercentual())));
        documento.add(new Paragraph("Adesão = (tomadas + confirmadas com atraso) ÷ (total - pendentes)", RODAPE));
        PdfPTable tabela = tabela(new float[]{1, 1, 1, 1, 1});
        cabecalhoDaTabela(tabela, "Total de doses", "Tomadas", "Com atraso", "Não tomadas", "Pendentes");
        celulas(tabela, String.valueOf(totais.total()), String.valueOf(totais.tomadas()),
                String.valueOf(totais.confirmadasComAtraso()), String.valueOf(totais.naoTomadas()),
                String.valueOf(totais.pendentes()));
        documento.add(tabela);
    }

    private static void porMedicamento(Document documento, RelatorioDeAdesao relatorio) {
        documento.add(new Paragraph("Adesão por medicamento", SUBTITULO));
        PdfPTable tabela = tabela(new float[]{2.4f, 1.3f, 1.1f, 1.3f, 1.3f, 1.4f, 1.3f, 1.6f});
        cabecalhoDaTabela(tabela, "Medicamento", "Dosagem", "Doses", "Tomadas", "Com atraso", "Não tomadas",
                "Pendentes", "Adesão");
        for (RelatorioDeAdesao.AdesaoDoMedicamento item : relatorio.porMedicamento()) {
            TotaisDoDia t = item.totais();
            celulas(tabela, item.nome(), item.dosagem(), String.valueOf(t.total()), String.valueOf(t.tomadas()),
                    String.valueOf(t.confirmadasComAtraso()), String.valueOf(t.naoTomadas()),
                    String.valueOf(t.pendentes()), taxa(t.aderenciaPercentual()));
        }
        documento.add(tabela);
    }

    private static void tabelaDeDoses(Document documento, FonteDeDoses doses) {
        documento.add(new Paragraph("Registro das doses", SUBTITULO));
        PdfPTable tabela = tabela(new float[]{1.3f, 1.5f, 3, 1.5f, 1.8f});
        tabela.setComplete(false);
        cabecalhoDaTabela(tabela, "Data", "Hora programada", "Medicamento", "Status", "Confirmação");
        tabela.setHeaderRows(1);
        doses.percorrer(lote -> {
            for (DoseDetalhada dose : lote) {
                LocalDateTime programada = dose.dose().getHorarioProgramado();
                celulas(tabela, programada.format(DATA), programada.format(HORA),
                        dose.medicamentoNome() + " " + dose.dosagem(), status(dose.dose().getStatus()),
                        confirmacao(programada, dose.dose().getHoraConfirmacao()));
            }
            // Envia as linhas do lote para o arquivo e libera da memória
            documento.add(tabela);
        });
        tabela.setComplete(true);
        documento.add(tabela);
    }

    static String status(StatusDose status) {
        return switch (status) {
            case TOMADA -> "Tomada";
            case CONFIRMADA_COM_ATRASO -> "Com atraso";
            case NAO_TOMADA -> "Não tomada";
            case PENDENTE -> "Pendente";
        };
    }

    /** Hora da confirmação; com a data quando foi em outro dia; "—" quando não houve. */
    static String confirmacao(LocalDateTime programada, LocalDateTime confirmada) {
        if (confirmada == null) {
            return "—";
        }
        return confirmada.toLocalDate().equals(programada.toLocalDate()) ? confirmada.format(HORA)
                : confirmada.format(DATA_HORA);
    }

    /** "87,5%" com 1 casa e vírgula; sem dose encerrada no período, a taxa não existe. */
    static String taxa(Double percentual) {
        return percentual == null ? "sem doses encerradas" : String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", percentual);
    }

    private static Paragraph linha(String rotulo, String valor) {
        Paragraph paragrafo = new Paragraph();
        paragrafo.add(new Chunk(rotulo, TEXTO_NEGRITO));
        paragrafo.add(new Chunk(valor, TEXTO));
        return paragrafo;
    }

    private static PdfPTable tabela(float[] larguras) {
        PdfPTable tabela = new PdfPTable(larguras);
        tabela.setWidthPercentage(100);
        tabela.setSpacingBefore(6);
        tabela.setSpacingAfter(12);
        return tabela;
    }

    private static void cabecalhoDaTabela(PdfPTable tabela, String... titulos) {
        for (String titulo : titulos) {
            PdfPCell celula = new PdfPCell(new Phrase(titulo, TABELA_CABECALHO));
            celula.setBackgroundColor(CINZA);
            celula.setPadding(4);
            tabela.addCell(celula);
        }
    }

    private static void celulas(PdfPTable tabela, String... valores) {
        for (String valor : valores) {
            PdfPCell celula = new PdfPCell(new Phrase(valor, TABELA));
            celula.setPadding(3);
            tabela.addCell(celula);
        }
    }

    /** Rodapé de cada página: número da página e a data de geração. */
    private static final class Rodape extends PdfPageEventHelper {

        private final String geradoEm;

        private Rodape(LocalDateTime geradoEm) {
            this.geradoEm = geradoEm.format(DATA_HORA);
        }

        @Override
        public void onEndPage(PdfWriter writer, Document documento) {
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT,
                    new Phrase("MedCare Sênior · histórico de adesão gerado em " + geradoEm, RODAPE),
                    documento.left(), documento.bottom() - 20, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT,
                    new Phrase("Página " + writer.getPageNumber(), RODAPE), documento.right(), documento.bottom() - 20, 0);
        }
    }
}
