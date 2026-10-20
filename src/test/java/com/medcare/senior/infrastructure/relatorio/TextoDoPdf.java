package com.medcare.senior.infrastructure.relatorio;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import java.io.IOException;

/**
 * Leitura de um PDF gerado, nos testes, com o extrator de texto do próprio OpenPDF.
 */
public final class TextoDoPdf {

    private TextoDoPdf() {
    }

    /** Texto de todas as páginas, na ordem. */
    public static String texto(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extrator = new PdfTextExtractor(reader);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extrator.getTextFromPage(pagina)).append('\n');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }

    /** O extrator quebra linhas dentro das células e junta células vizinhas: compara sem espaços em branco. */
    public static String semEspacos(String texto) {
        return texto.replaceAll("\\s+", "");
    }

    public static int paginas(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }
}
