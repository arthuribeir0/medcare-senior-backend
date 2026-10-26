import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Resume o resultado (.jtl em CSV, padrão do JMeter) por cenário e confere os critérios de aceitação do README.
 * Sem dependências: {@code java docs/carga/ResumoCarga.java saida/resultado.jtl}
 *
 * <p>Os cenários cujo nome começa com "Leitura" ou "Escrita" entram nos critérios de p95 de cada tipo; os de
 * preparação da sessão (login inicial, código de vínculo) entram como escrita.
 */
public class ResumoCarga {

    static final long P95_LEITURA_MS = 500;
    static final long P95_ESCRITA_MS = 800;
    static final double ERRO_MAXIMO = 0.01;

    record Amostra(long inicio, long tempo, String cenario, String codigo, boolean sucesso) {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Uso: java ResumoCarga.java <resultado.jtl>");
            System.exit(2);
        }
        // Saída sempre em UTF-8 (no Windows, o console do Java 17 usaria a página de código local)
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8));
        List<Amostra> amostras = ler(Path.of(args[0]));
        if (amostras.isEmpty()) {
            System.err.println("Nenhuma amostra no arquivo");
            System.exit(2);
        }
        Map<String, List<Amostra>> porCenario = amostras.stream()
                .collect(Collectors.groupingBy(Amostra::cenario, TreeMap::new, Collectors.toList()));

        System.out.println("| Cenário | Amostras | Média (ms) | p90 | p95 | p99 | Vazão (req/s) | Erro |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|");
        porCenario.forEach((cenario, lista) -> System.out.println(linha(cenario, lista)));
        System.out.println(linha("**Total**", amostras));

        List<Amostra> leituras = amostras.stream().filter(a -> a.cenario().startsWith("Leitura")).toList();
        List<Amostra> escritas = amostras.stream().filter(a -> a.cenario().startsWith("Escrita")).toList();
        long p95Leitura = percentil(leituras, 95);
        long p95Escrita = percentil(escritas, 95);
        double erro = taxaDeErro(amostras);
        long erros5xx = amostras.stream().filter(a -> a.codigo().startsWith("5")).count();

        System.out.println();
        System.out.println("| Critério | Limite | Medido | Resultado |");
        System.out.println("|---|---|---|---|");
        criterio("p95 das leituras", "≤ " + P95_LEITURA_MS + " ms", p95Leitura + " ms", p95Leitura <= P95_LEITURA_MS);
        criterio("p95 das escritas", "≤ " + P95_ESCRITA_MS + " ms", p95Escrita + " ms", p95Escrita <= P95_ESCRITA_MS);
        criterio("Erros", "< 1%", porcentagem(erro), erro < ERRO_MAXIMO);
        criterio("Respostas 5xx", "0", String.valueOf(erros5xx), erros5xx == 0);

        Map<String, Long> falhas = amostras.stream().filter(a -> !a.sucesso())
                .collect(Collectors.groupingBy(a -> a.cenario() + " → " + a.codigo(), TreeMap::new, Collectors.counting()));
        if (!falhas.isEmpty()) {
            System.out.println();
            System.out.println("Falhas por cenário e código HTTP:");
            falhas.forEach((chave, total) -> System.out.println("- " + chave + ": " + total));
        }
    }

    static List<Amostra> ler(Path arquivo) throws IOException {
        List<String> linhas = Files.readAllLines(arquivo, StandardCharsets.UTF_8);
        List<String> cabecalho = Arrays.asList(linhas.get(0).split(","));
        int inicio = cabecalho.indexOf("timeStamp");
        int tempo = cabecalho.indexOf("elapsed");
        int cenario = cabecalho.indexOf("label");
        int codigo = cabecalho.indexOf("responseCode");
        int sucesso = cabecalho.indexOf("success");
        List<Amostra> amostras = new ArrayList<>();
        for (String linha : linhas.subList(1, linhas.size())) {
            String[] campos = csv(linha);
            if (campos.length <= Math.max(sucesso, cenario)) {
                continue;
            }
            amostras.add(new Amostra(Long.parseLong(campos[inicio]), Long.parseLong(campos[tempo]), campos[cenario],
                    campos[codigo], Boolean.parseBoolean(campos[sucesso])));
        }
        return amostras;
    }

    /** Divide uma linha CSV respeitando campos entre aspas (mensagens de falha podem ter vírgula). */
    static String[] csv(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean aspas = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                aspas = !aspas;
            } else if (c == ',' && !aspas) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos.toArray(String[]::new);
    }

    static String linha(String cenario, List<Amostra> lista) {
        double media = lista.stream().mapToLong(Amostra::tempo).average().orElse(0);
        long primeiro = lista.stream().mapToLong(Amostra::inicio).min().orElse(0);
        long ultimo = lista.stream().mapToLong(a -> a.inicio() + a.tempo()).max().orElse(0);
        double segundos = Math.max(1, ultimo - primeiro) / 1000.0;
        return String.format(Locale.ROOT, "| %s | %d | %.1f | %d | %d | %d | %.2f | %s |", cenario, lista.size(), media,
                percentil(lista, 90), percentil(lista, 95), percentil(lista, 99), lista.size() / segundos,
                porcentagem(taxaDeErro(lista)));
    }

    /** Percentil pelo método do valor mais próximo (o mesmo do relatório HTML do JMeter). */
    static long percentil(List<Amostra> lista, int p) {
        if (lista.isEmpty()) {
            return 0;
        }
        long[] tempos = lista.stream().mapToLong(Amostra::tempo).sorted().toArray();
        int indice = (int) Math.ceil(p / 100.0 * tempos.length) - 1;
        return tempos[Math.max(0, Math.min(indice, tempos.length - 1))];
    }

    static double taxaDeErro(List<Amostra> lista) {
        return lista.isEmpty() ? 0 : (double) lista.stream().filter(a -> !a.sucesso()).count() / lista.size();
    }

    static String porcentagem(double valor) {
        return String.format(Locale.ROOT, "%.2f%%", valor * 100);
    }

    static void criterio(String nome, String limite, String medido, boolean passou) {
        System.out.println("| " + nome + " | " + limite + " | " + medido + " | " + (passou ? "passou" : "**não passou**") + " |");
    }
}
