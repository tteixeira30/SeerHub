package pt.seerhub.tips.parser.market;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import pt.seerhub.tips.domain.Market;

/** Dupla hipótese: {@code 1X}/{@code 12}/{@code X2} e as escritas invertidas {@code X1}/{@code 21}/{@code 2X}. */
public final class DoubleChanceMarket implements MarketDefinition {

    private static final Set<String> TOKENS_DE_MERCADO = Set.of("DC");

    @Override
    public Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao) {
        if (tokenDeMercado != null && !TOKENS_DE_MERCADO.contains(tokenDeMercado.toUpperCase(Locale.ROOT))) {
            return Optional.empty();
        }
        String selecao = mapearSelecao(tokenDeSelecao);
        if (selecao == null) {
            return Optional.empty();
        }
        return Optional.of(new MarketMatch(Market.DOUBLE_CHANCE, selecao, null));
    }

    private String mapearSelecao(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "1X", "X1" -> "HOME_DRAW";
            case "12", "21" -> "HOME_AWAY";
            case "X2", "2X" -> "DRAW_AWAY";
            default -> null;
        };
    }

    @Override
    public String nome() {
        return "DOUBLE_CHANCE";
    }

    @Override
    public List<String> exemplos() {
        return List.of("1X", "12", "X2", "DC 1X");
    }

    @Override
    public boolean reconheceTokenDeMercado(String token) {
        return token != null && TOKENS_DE_MERCADO.contains(token.toUpperCase(Locale.ROOT));
    }

    @Override
    public List<String> selecoesAceites() {
        return List.of("1X", "12", "X2", "X1", "21", "2X");
    }
}
