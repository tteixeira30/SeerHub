package pt.seerhub.tips.domain;

/**
 * A taxonomia de mercados persistida — espelho exato do {@code CHECK ck_selection_market}
 * de {@code V2__baseline_schema.sql}. Enumerado burro, sem comportamento de parsing: é sobre
 * ele que R8 despacha a resolução automática. O reconhecimento de tokens do texto colado
 * vive em {@code pt.seerhub.tips.parser.market} (interface {@code MarketDefinition}).
 */
public enum Market {
    MATCH_RESULT,
    DOUBLE_CHANCE,
    OVER_UNDER,
    BTTS,
    HANDICAP
}
