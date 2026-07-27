package pt.seerhub.tips.parser.market;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import pt.seerhub.tips.domain.Market;

/**
 * Ambas marcam: {@code BTTS S}/{@code BTTS N}/{@code AM S}/{@code AM N} (separado) e {@code
 * GG}/{@code NG} (fundido). {@code S} isolado, sem o token de mercado, não é reconhecido —
 * fica deliberadamente ambíguo demais para aceitar.
 */
public final class BttsMarket implements MarketDefinition {

    private static final Set<String> TOKENS_DE_MERCADO = Set.of("BTTS", "AM");

    @Override
    public Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao) {
        if (tokenDeMercado == null) {
            return reconhecerFundido(tokenDeSelecao);
        }
        if (!reconheceTokenDeMercado(tokenDeMercado)) {
            return Optional.empty();
        }
        String selecao = mapearSN(tokenDeSelecao);
        if (selecao == null) {
            return Optional.empty();
        }
        return Optional.of(new MarketMatch(Market.BTTS, selecao, null));
    }

    private Optional<MarketMatch> reconhecerFundido(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "GG" -> Optional.of(new MarketMatch(Market.BTTS, "YES", null));
            case "NG" -> Optional.of(new MarketMatch(Market.BTTS, "NO", null));
            default -> Optional.empty();
        };
    }

    private String mapearSN(String token) {
        if (token == null) {
            return null;
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "S" -> "YES";
            case "N" -> "NO";
            default -> null;
        };
    }

    @Override
    public String nome() {
        return "BTTS";
    }

    @Override
    public List<String> exemplos() {
        return List.of("BTTS S", "BTTS N", "AM S", "GG", "NG");
    }

    @Override
    public boolean reconheceTokenDeMercado(String token) {
        return token != null && TOKENS_DE_MERCADO.contains(token.toUpperCase(Locale.ROOT));
    }

    @Override
    public List<String> selecoesAceites() {
        return List.of("S", "N");
    }
}
