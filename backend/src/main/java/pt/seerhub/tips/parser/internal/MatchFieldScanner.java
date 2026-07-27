package pt.seerhub.tips.parser.internal;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.seerhub.tips.parser.ParseError;
import pt.seerhub.tips.parser.ParseErrorCode;
import pt.seerhub.tips.parser.TeamRef;

/**
 * Lê o primeiro segmento de uma linha: data opcional (com inferência do ano a partir de
 * {@code catalog.hoje()}) e o par de equipas, com as colunas absolutas de cada equipa sobre a
 * linha original (§4.4 do plano).
 */
public final class MatchFieldScanner {

    // Sintaxe válida de data: dd/mm.
    private static final Pattern DATA_VALIDA_SINTAXE = Pattern.compile("^\\d{1,2}/\\d{1,2}$");
    // Forma "parece uma tentativa de data" mas pode ser sintaticamente malformada
    // (ex.: "2807", "28-07") — ainda assim tratada como tentativa de data, não como nome de equipa.
    private static final Pattern PARECE_DATA = Pattern.compile("^\\d{1,4}([/-]\\d{1,4})?$");

    // Separador do par: espaços/tabs obrigatórios dos dois lados, e um destes tokens
    // (case-insensitive cobre X/x, VS/vs/Vs/vS, V/v).
    private static final Pattern SEPARADOR = Pattern.compile(
            "[ \\t]+(-|\u2013|\u2014|vs|v|x)[ \\t]+", Pattern.CASE_INSENSITIVE);

    private MatchFieldScanner() {
    }

    public record Resultado(LocalDate data, String textoDaData, TeamRef casa, TeamRef fora, ParseError erro) {

        public boolean sucesso() {
            return erro == null;
        }
    }

    public static Resultado ler(Segment segmento, int linha, LocalDate hoje) {
        String texto = segmento.texto();
        int i = 0;
        while (i < texto.length() && !Character.isWhitespace(texto.charAt(i))) {
            i++;
        }
        String primeiroToken = texto.substring(0, i);

        LocalDate data = null;
        String textoDaData = null;
        String restante = texto;
        int colBase = segmento.colunaInicial();

        if (i < texto.length() && PARECE_DATA.matcher(primeiroToken).matches()) {
            if (!DATA_VALIDA_SINTAXE.matcher(primeiroToken).matches()) {
                return new Resultado(null, null, null, null, erroData(segmento, linha));
            }
            String[] partes = primeiroToken.split("/");
            int dia = Integer.parseInt(partes[0]);
            int mes = Integer.parseInt(partes[1]);
            LocalDate inferida = inferirAno(dia, mes, hoje);
            if (inferida == null) {
                return new Resultado(null, null, null, null, erroData(segmento, linha));
            }
            data = inferida;
            textoDaData = primeiroToken;

            int j = i;
            while (j < texto.length() && Character.isWhitespace(texto.charAt(j))) {
                j++;
            }
            restante = texto.substring(j);
            colBase = segmento.colunaInicial() + j;
        }

        return lerParDeEquipas(restante, colBase, linha, data, textoDaData);
    }

    private static Resultado lerParDeEquipas(String restante, int colBase, int linha, LocalDate data, String textoDaData) {
        Matcher m = SEPARADOR.matcher(restante);
        List<int[]> ocorrencias = new ArrayList<>();
        while (m.find()) {
            ocorrencias.add(new int[]{m.start(), m.end()});
        }

        if (ocorrencias.isEmpty()) {
            return new Resultado(null, null, null, null, new ParseError(linha, colBase, restante,
                    ParseErrorCode.PAR_DE_EQUIPAS_INVALIDO,
                    "Não encontrei o separador do par de equipas. Use \" - \", \" x \" ou \" vs \" entre os dois nomes."));
        }
        if (ocorrencias.size() >= 2) {
            int colunaSegunda = colBase + ocorrencias.get(1)[0];
            return new Resultado(null, null, null, null, new ParseError(linha, colunaSegunda, restante,
                    ParseErrorCode.PAR_DE_EQUIPAS_AMBIGUO,
                    "Encontrei mais do que um separador de equipas nesta linha. "
                            + "Deixe só um \" - \"/\" x \"/\" vs \" entre as duas equipas."));
        }

        int inicioSep = ocorrencias.get(0)[0];
        int fimSep = ocorrencias.get(0)[1];
        String casaTexto = restante.substring(0, inicioSep);
        String foraTexto = restante.substring(fimSep);

        if (casaTexto.isEmpty() || foraTexto.isEmpty()) {
            int coluna = casaTexto.isEmpty() ? colBase : colBase + fimSep;
            return new Resultado(null, null, null, null, new ParseError(linha, coluna, restante,
                    ParseErrorCode.EQUIPA_VAZIA,
                    "Falta o nome de uma das equipas."));
        }

        TeamRef casa = new TeamRef(casaTexto, linha, colBase);
        TeamRef fora = new TeamRef(foraTexto, linha, colBase + fimSep);
        return new Resultado(data, textoDaData, casa, fora, null);
    }

    /**
     * Candidatos {@code hoje.ano-1}, {@code hoje.ano}, {@code hoje.ano+1}; descarta os que não
     * formam uma data válida (ex.: 29/02 fora de ano bissexto); escolhe o de menor distância
     * absoluta em dias a {@code hoje}, com desempate no futuro. {@code null} se nenhum for válido.
     */
    private static LocalDate inferirAno(int dia, int mes, LocalDate hoje) {
        LocalDate melhor = null;
        for (int deltaAno = -1; deltaAno <= 1; deltaAno++) {
            LocalDate candidato;
            try {
                candidato = LocalDate.of(hoje.getYear() + deltaAno, mes, dia);
            } catch (DateTimeException e) {
                continue;
            }
            if (melhor == null) {
                melhor = candidato;
                continue;
            }
            long distanciaAtual = Math.abs(ChronoUnit.DAYS.between(hoje, melhor));
            long distanciaCandidata = Math.abs(ChronoUnit.DAYS.between(hoje, candidato));
            if (distanciaCandidata < distanciaAtual
                    || (distanciaCandidata == distanciaAtual && !candidato.isBefore(hoje) && melhor.isBefore(hoje))) {
                melhor = candidato;
            }
        }
        return melhor;
    }

    private static ParseError erroData(Segment segmento, int linha) {
        return new ParseError(linha, segmento.colunaInicial(), segmento.texto(), ParseErrorCode.DATA_INVALIDA,
                "Data inválida. Use o formato \"dd/mm\", ex.: \"28/07\".");
    }
}
