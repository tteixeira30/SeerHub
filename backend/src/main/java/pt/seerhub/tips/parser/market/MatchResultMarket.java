package pt.seerhub.tips.parser.market;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import pt.seerhub.tips.domain.Market;

/** Resultado final (1X2): {@code 1}/{@code X}/{@code 2}, com token de mercado opcional. */
public final class MatchResultMarket implements MarketDefinition {

    private static final Set<String> TOKENS_DE_MERCADO = Set.of("1X2", "MR", "FT", "RESULTADO");

    @Override
    public Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao) {
        if (tokenDeMercado != null && !TOKENS_DE_MERCADO.contains(tokenDeMercado.toUpperCase(Locale.ROOT))) {
            return Optional.empty();
        }
        String selecao = mapearSelecao(tokenDeSelecao);
        if (selecao == null) {
            return Optional.empty();
        }
        return Optional.of(new MarketMatch(Market.MATCH_RESULT, selecao, null));
    }

    private String mapearSelecao(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "1" -> "HOME";
            case "X" -> "DRAW";
            case "2" -> "AWAY";
            default -> null;
        };
    }

    @Override
    public String nome() {
        return "MATCH_RESULT";
    }

    @Override
    public List<String> exemplos() {
        return List.of("1X2 1", "1X2 X", "1X2 2", "1 (sozinho)");
    }

    @Override
    public boolean reconheceTokenDeMercado(String token) {
        return token != null && TOKENS_DE_MERCADO.contains(token.toUpperCase(Locale.ROOT));
    }

    @Override
    public List<String> selecoesAceites() {
        return List.of("1", "X", "2");
    }
}
