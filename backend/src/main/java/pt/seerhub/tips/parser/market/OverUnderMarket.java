package pt.seerhub.tips.parser.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.seerhub.tips.domain.Market;

/** Mais/menos golos: {@code O2.5}/{@code U3.5} fundido, ou {@code O 2.5}/{@code UNDER 3.5} separado. */
public final class OverUnderMarket implements MarketDefinition {

    private static final Pattern FUNDIDO = Pattern.compile("^(O|OVER|U|UNDER)(\\d{1,2}([.,]\\d{1,2})?)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERO = Pattern.compile("^\\d{1,2}([.,]\\d{1,2})?$");

    @Override
    public Optional<MarketMatch> reconhecer(String tokenDeMercado, String tokenDeSelecao) {
        if (tokenDeMercado == null) {
            if (tokenDeSelecao == null) {
                return Optional.empty();
            }
            Matcher m = FUNDIDO.matcher(tokenDeSelecao);
            if (!m.matches()) {
                return Optional.empty();
            }
            return Optional.of(construir(m.group(1), m.group(2)));
        }
        if (!reconheceTokenDeMercado(tokenDeMercado) || tokenDeSelecao == null || !NUMERO.matcher(tokenDeSelecao).matches()) {
            return Optional.empty();
        }
        return Optional.of(construir(tokenDeMercado, tokenDeSelecao));
    }

    private MarketMatch construir(String letraOuPalavra, String numeroTexto) {
        boolean over = letraOuPalavra.toUpperCase(Locale.ROOT).startsWith("O");
        BigDecimal linha = new BigDecimal(numeroTexto.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        return new MarketMatch(Market.OVER_UNDER, over ? "OVER" : "UNDER", linha);
    }

    @Override
    public String nome() {
        return "OVER_UNDER";
    }

    @Override
    public List<String> exemplos() {
        return List.of("O2.5", "U3.5", "OVER 2.5", "UNDER 3.5");
    }

    @Override
    public boolean reconheceTokenDeMercado(String token) {
        if (token == null) {
            return false;
        }
        String maiusculas = token.toUpperCase(Locale.ROOT);
        return maiusculas.equals("O") || maiusculas.equals("OVER") || maiusculas.equals("U") || maiusculas.equals("UNDER");
    }

    @Override
    public List<String> selecoesAceites() {
        return List.of("2.5 (depois de O/OVER ou U/UNDER)");
    }
}
