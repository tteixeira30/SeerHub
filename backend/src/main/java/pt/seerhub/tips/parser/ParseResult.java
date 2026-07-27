package pt.seerhub.tips.parser;

import java.util.List;

import pt.seerhub.tips.domain.ImportStatus;

/**
 * O resultado de {@link TipTextParser#parse(String, TipCatalog)}. {@code rawText} devolve o
 * texto original byte a byte, sempre — mesmo quando tudo falha (o {@code TipImport} de F07
 * depende disto, §8/§9 da spec). {@code formatoNaoReconhecido} é {@code true} sse houve pelo
 * menos uma linha não vazia e zero tips — a bandeira dedicada da §10 da spec para o caso do
 * tipster colar o formato do Telegram dele.
 */
public record ParseResult(
        String rawText,
        String parserVersion,
        List<ParsedTip> tips,
        List<ParseError> erros,
        boolean formatoNaoReconhecido) {

    public ParseResult {
        tips = List.copyOf(tips);
        erros = List.copyOf(erros);
    }

    /** OK sem erros; FAILED sem nenhuma tip válida; PARTIAL nos restantes casos. */
    public ImportStatus estado() {
        if (erros.isEmpty()) {
            return ImportStatus.OK;
        }
        return tips.isEmpty() ? ImportStatus.FAILED : ImportStatus.PARTIAL;
    }

    public int totalSelecoes() {
        return tips.stream().mapToInt(t -> t.selecoes().size()).sum();
    }

    public boolean temErros() {
        return !erros.isEmpty();
    }

    public static ParseResult vazio(String rawText) {
        return new ParseResult(rawText, TipFormat.VERSAO, List.of(), List.of(), false);
    }
}
