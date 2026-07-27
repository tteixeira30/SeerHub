package pt.seerhub.tips.parser.market;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pt.seerhub.tips.domain.Market;

/**
 * Handicap: {@code H-1}/{@code H+1.5}/{@code H1-1}/{@code H2+1.5} fundido, ou a forma separada
 * ({@code H}/{@code HA}/{@code AH}, com o dígito de equipa opcional) + linha com sinal. Sem
 * indicação de equipa (o {@code H} sozinho), assume-se {@code HOME} por convenção de mercado
 * (§9.2, risco 2, do plano) — {@code H1}/{@code H2} são o escape explícito.
 */
public final class HandicapMarket implements MarketDefinition {

    private static final Pattern FUNDIDO = Pattern.compile("^H([12])?([-+]\\d{1,2}([.,]\\d{1,2})?)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_MERCADO = Pattern.compile("^(H|HA|AH)([12])?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINHA_COM_SINAL = Pattern.compile("^[-+]\\d{1,2}([.,]\\d{1,2})?$");

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
        Matcher mToken = TOKEN_MERCADO.matcher(tokenDeMercado);
        if (!mToken.matches() || tokenDeSelecao == null || !LINHA_COM_SINAL.matcher(tokenDeSelecao).matches()) {
            return Optional.empty();
        }
        return Optional.of(construir(mToken.group(2), tokenDeSelecao));
    }

    private MarketMatch construir(String digitoEquipa, String linhaComSinal) {
        String selecao = "2".equals(digitoEquipa) ? "AWAY" : "HOME";
        BigDecimal linha = new BigDecimal(linhaComSinal.replace(',', '.')).setScale(2, RoundingMode.HALF_UP);
        return new MarketMatch(Market.HANDICAP, selecao, linha);
    }

    @Override
    public String nome() {
        return "HANDICAP";
    }

    @Override
    public List<String> exemplos() {
        return List.of("H-1", "H+1.5", "H1-1", "H2+1.5", "H -1", "HA +1.5");
    }

    @Override
    public boolean reconheceTokenDeMercado(String token) {
        return token != null && TOKEN_MERCADO.matcher(token.toUpperCase(Locale.ROOT)).matches();
    }

    @Override
    public List<String> selecoesAceites() {
        return List.of("-1 (ou qualquer linha com sinal, ex.: +1.5)");
    }
}
