package pt.seerhub.tips.parser.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Parte o texto em linhas lógicas (1-based, terminações {@code \r\n} ou {@code \n}, BOM
 * inicial removido) e cada linha em {@link Segment}s por {@code |}, guardando o deslocamento
 * absoluto de cada segmento na linha original. A linha original nunca é reescrita (nem para
 * normalizar tabulações) — os scanners saltam espaços, mas as colunas vêm sempre do texto tal
 * como foi colado (§4.4/H2 do plano).
 */
public final class LineSplitter {

    private LineSplitter() {
    }

    /** Uma linha lógica do texto original, 1-based, com o seu texto verbatim. */
    public record Linha(int numero, String textoOriginal) {

        public boolean vazia() {
            return textoOriginal.isBlank();
        }

        /** A linha começa por espaço ou tabulação — significado só dentro de um bloco MULT aberto. */
        public boolean indentada() {
            return !textoOriginal.isEmpty()
                    && (textoOriginal.charAt(0) == ' ' || textoOriginal.charAt(0) == '\t');
        }
    }

    private static final char BOM = (char) 0xFEFF;

    /**
     * Divide o texto em linhas 1-based. Um BOM inicial ({@code U+FEFF}) é descartado. Uma
     * terminação de linha final não gera uma linha fantasma extra a seguir.
     */
    public static List<Linha> dividirEmLinhas(String rawText) {
        String semBom = !rawText.isEmpty() && rawText.charAt(0) == BOM ? rawText.substring(1) : rawText;
        String[] partes = semBom.split("\r\n|\n", -1);
        int quantas = partes.length;
        if (quantas > 0 && partes[quantas - 1].isEmpty() && terminaEmQuebraDeLinha(semBom)) {
            quantas--;
        }
        List<Linha> linhas = new ArrayList<>(quantas);
        for (int i = 0; i < quantas; i++) {
            linhas.add(new Linha(i + 1, partes[i]));
        }
        return linhas;
    }

    private static boolean terminaEmQuebraDeLinha(String texto) {
        return texto.endsWith("\n") || texto.endsWith("\r");
    }

    /** Divide uma única linha (já sem terminação) em segmentos por {@code |}. Devolve sempre ≥1. */
    public static List<Segment> segmentar(String textoDaLinha) {
        List<Segment> segmentos = new ArrayList<>();
        int inicioBruto = 0;
        for (int i = 0; i < textoDaLinha.length(); i++) {
            if (textoDaLinha.charAt(i) == '|') {
                segmentos.add(paraSegmento(textoDaLinha, inicioBruto, i));
                inicioBruto = i + 1;
            }
        }
        segmentos.add(paraSegmento(textoDaLinha, inicioBruto, textoDaLinha.length()));
        return segmentos;
    }

    private static Segment paraSegmento(String linha, int inicio0based, int fimExclusivo0based) {
        int ini = inicio0based;
        int fim = fimExclusivo0based - 1;
        while (ini <= fim && isEspacoOuTab(linha.charAt(ini))) {
            ini++;
        }
        while (fim >= ini && isEspacoOuTab(linha.charAt(fim))) {
            fim--;
        }
        if (ini > fim) {
            int coluna = inicio0based + 1;
            return new Segment("", coluna, coluna);
        }
        String texto = linha.substring(ini, fim + 1);
        return new Segment(texto, ini + 1, fim + 1);
    }

    private static boolean isEspacoOuTab(char c) {
        return c == ' ' || c == '\t';
    }
}
