package pt.seerhub.tips.parser;

import java.math.BigDecimal;
import java.time.LocalDate;

import pt.seerhub.tips.domain.Market;

/**
 * Uma seleção pronta para revisão, ainda sem {@code fixture_id}. {@code casa}/{@code fora}
 * trazem sempre o texto cru da equipa (§2.5 do plano); {@code resolucao} é sempre {@link
 * TeamResolution#porResolver()} em F06a. {@link #comResolucao(TeamResolution)} e {@link
 * #comData(LocalDate)} são as duas únicas portas por onde F06b altera um resultado — por
 * decoração, nunca por edição de {@link GrammarTipTextParser}.
 */
public record ParsedSelection(
        int linha,
        LocalDate data,
        String textoDaData,
        TeamRef casa,
        TeamRef fora,
        Market mercado,
        String selecao,
        BigDecimal linhaMercado,
        BigDecimal odd,
        TeamResolution resolucao) {

    /** Porta de F06b: devolve uma cópia com a resolução de equipa substituída. */
    public ParsedSelection comResolucao(TeamResolution nova) {
        return new ParsedSelection(linha, data, textoDaData, casa, fora, mercado, selecao, linhaMercado, odd, nova);
    }

    /** Porta de F06b: devolve uma cópia com a data substituída (ex.: inferida do jogo ligado). */
    public ParsedSelection comData(LocalDate nova) {
        return new ParsedSelection(linha, nova, textoDaData, casa, fora, mercado, selecao, linhaMercado, odd, resolucao);
    }
}
