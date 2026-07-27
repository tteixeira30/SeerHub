package pt.seerhub.tips.parser;

import java.math.BigDecimal;
import java.util.List;

/**
 * Uma tip pronta para revisão, ainda sem {@code fixture_id} em nenhuma das suas seleções.
 * {@code linha} é a linha da tip simples, ou a linha do cabeçalho {@code MULT} quando {@code
 * tipo == MULTIPLA}.
 */
public record ParsedTip(
        int linha,
        TipKind tipo,
        BigDecimal stakeUnidades,
        BigDecimal oddTotal,
        List<ParsedSelection> selecoes,
        String textoOriginal) {

    public ParsedTip {
        selecoes = List.copyOf(selecoes);
    }
}
