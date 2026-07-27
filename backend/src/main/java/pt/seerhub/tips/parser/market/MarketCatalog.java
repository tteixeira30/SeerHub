package pt.seerhub.tips.parser.market;

import java.util.List;
import java.util.Optional;

/**
 * O catálogo de mercados da gramática, ordenado e imutável. Acrescentar um mercado = uma
 * classe {@link MarketDefinition} nova + uma linha em {@link #PADRAO} (+ migração para o
 * {@code CHECK} de {@code tip_selections.market}, se for uma família nova) — §9.1/H3 do plano.
 */
public final class MarketCatalog {

    public static final List<MarketDefinition> PADRAO = List.of(
            new MatchResultMarket(),
            new DoubleChanceMarket(),
            new OverUnderMarket(),
            new BttsMarket(),
            new HandicapMarket());

    private MarketCatalog() {
    }

    /** Percorre {@link #PADRAO} pela ordem e devolve o primeiro reconhecimento. */
    public static Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao) {
        for (MarketDefinition definicao : PADRAO) {
            Optional<MarketMatch> match = definicao.reconhecer(tokenDeMercado, tokenDeSelecao);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    /** Exemplos de todos os mercados, para a mensagem de {@code MERCADO_DESCONHECIDO}. */
    public static List<String> exemplosDeTodosOsMercados() {
        return PADRAO.stream().flatMap(d -> d.exemplos().stream()).toList();
    }

    /** Só para diagnóstico: qual definição reconhece este token como o seu token de mercado. */
    public static Optional<MarketDefinition> definicaoQueReconheceOToken(String tokenDeMercado) {
        for (MarketDefinition definicao : PADRAO) {
            if (definicao.reconheceTokenDeMercado(tokenDeMercado)) {
                return Optional.of(definicao);
            }
        }
        return Optional.empty();
    }
}
