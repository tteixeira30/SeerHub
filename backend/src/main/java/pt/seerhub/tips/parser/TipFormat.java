package pt.seerhub.tips.parser;

import java.math.BigDecimal;

/**
 * Constantes públicas do formato — a versão do parser, os limites que espelham o baseline
 * (§2.3/§4.3 do plano) e o bloco de exemplo literal da spec (R6, "Formato (v1)"). É daqui que
 * F07 tira o exemplo a mostrar ao lado do texto colado quando {@link
 * ParseResult#formatoNaoReconhecido()} é {@code true}.
 */
public final class TipFormat {

    private TipFormat() {
    }

    /** Cabe em {@code tip_imports.parser_version VARCHAR(20)}. */
    public static final String VERSAO = "grammar-1";

    /** Acima disto, o bloco nem é varrido — {@code BLOCO_DEMASIADO_GRANDE} (12d). */
    public static final int LIMITE_DE_CARACTERES = 20_000;

    public static final BigDecimal STAKE_MINIMA = new BigDecimal("0.25");
    public static final BigDecimal STAKE_MAXIMA = new BigDecimal("10.00");
    public static final BigDecimal ODD_MINIMA = new BigDecimal("1.01");
    public static final BigDecimal ODD_MAXIMA = new BigDecimal("999.999");
    public static final BigDecimal ODD_TOTAL_MAXIMA = new BigDecimal("999.999");

    /**
     * O bloco literal do exemplo do R6 (spec, secção "Formato (v1)"), verbatim — inclui um
     * bloco {@code MULT} de duas seleções. É um bloco válido por inteiro (E5): três tips
     * simples mais uma tip múltipla de duas seleções, zero erros.
     */
    public static final String EXEMPLO = """
            28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u
            28/07 Arsenal - Chelsea | O2.5 | 1.90 | 1u
            29/07 Girona - Real Madrid | BTTS S | 1.72 | 1u
            MULT | 2u
              29/07 Bayern - Leipzig | 1 | 1.40
              30/07 Inter - Napoli | 1X | 1.55
            """;
}
