package pt.seerhub.tips.parser.market;

import java.math.BigDecimal;

import pt.seerhub.tips.domain.Market;

/** O resultado do reconhecimento de um segmento de mercado. {@code linha} é nula fora de OVER_UNDER e HANDICAP. */
public record MarketMatch(Market mercado, String selecao, BigDecimal linha) {
}
