package pt.seerhub.support;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import pt.seerhub.tips.parser.SimpleTipCatalog;
import pt.seerhub.tips.parser.TipCatalog;
import pt.seerhub.tips.parser.TipFormat;

/**
 * Corpus partilhado dos testes de F06a — mesmo padrão de {@code FootballTestSupport}/{@code
 * AuthTestSupport} (F00/F05).
 *
 * <p>{@link #CINCO_SIMPLES_E_UM_ACUMULADOR} usa deliberadamente as mesmas oito equipas do
 * catálogo fixo de F05 ({@code FixedFootballDataProvider}: Benfica–Porto, Sporting–Braga,
 * Arsenal–Chelsea, Man Utd–Liverpool, Girona–Real Madrid, Inter–Napoli, Bayern–Leipzig,
 * PSG–Marselha) — não para as resolver (F06a não liga equipa nenhuma), mas para que o mesmo
 * corpus sirva depois a F06b para provar as ligações reais sem inventar dados novos (§9.2,
 * risco 7, do plano de F06a).
 */
public final class TipTextSamples {

    private TipTextSamples() {
    }

    /** O bloco literal do exemplo do R6 — alias de {@link TipFormat#EXEMPLO}. */
    public static final String EXEMPLO_DA_SPEC = TipFormat.EXEMPLO;

    /** R6-10: 5 tips simples + 1 acumulador de 3 jogos → 6 tips, 8 seleções. */
    public static final String CINCO_SIMPLES_E_UM_ACUMULADOR = """
            28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u
            28/07 Sporting CP - SC Braga | 1X | 1.60 | 1u
            29/07 Arsenal - Chelsea | O2.5 | 1.90 | 1u
            29/07 Manchester United - Liverpool | BTTS S | 1.72 | 1u
            30/07 Girona - Real Madrid | H-1 | 2.10 | 1.5u
            MULT | 2u
              31/07 Internazionale - Napoli | 1 | 1.40
              31/07 Bayern München - RB Leipzig | 1X | 1.55
              01/08 Paris Saint-Germain - Olympique Marseille | O2.5 | 1.65
            """;

    /** As 16 equipas do corpus acima, na ordem em que aparecem (E10c). */
    public static final List<String> EQUIPAS_DO_CORPUS_FIXO = List.of(
            "Benfica", "Porto",
            "Sporting CP", "SC Braga",
            "Arsenal", "Chelsea",
            "Manchester United", "Liverpool",
            "Girona", "Real Madrid",
            "Internazionale", "Napoli",
            "Bayern München", "RB Leipzig",
            "Paris Saint-Germain", "Olympique Marseille");

    /** O caso de fronteira §10 da spec: formato do Telegram em vez do formato do SeerHub. */
    public static final String FORMATO_DE_TELEGRAM = """
            🔥 Benfica vence @1.85 ✅
            ⚽ Porto - Sporting hoje às 20h!
            Aposta: Over 2.5 @1.90
            """;

    /** Uma linha válida com tabulações e espaços misturados nos separadores — 4j. */
    public static final String TABS_E_ESPACOS =
            "28/07\tBenfica\t-\tPorto | 1X2 1 | abc | 2u\n";

    /** Um bloco com pelo menos 2000 carateres, todo em linhas válidas — 12a/12c. */
    public static final String BLOCO_DE_2000_CARACTERES = construirBlocoRepetido(2000);

    public static final String BLOCO_ADVERSARIAL_X = "x".repeat(2000);
    public static final String BLOCO_ADVERSARIAL_PIPE = "|".repeat(2000);
    public static final String BLOCO_ADVERSARIAL_HIFEN = "-".repeat(2000);
    public static final String BLOCO_ADVERSARIAL_ESPACOS = " ".repeat(2000);
    public static final String BLOCO_ADVERSARIAL_BARRA = "/".repeat(2000);

    private static String construirBlocoRepetido(int minimoDeCarateres) {
        String linha = "28/07 Benfica - Porto | 1X2 1 | 1.85 | 2u\n";
        StringBuilder sb = new StringBuilder();
        while (sb.length() < minimoDeCarateres) {
            sb.append(linha);
        }
        return sb.toString();
    }

    /** Catálogo de teste com {@code hoje} fixo e sem jogos — F06a nunca lê {@code jogos()}. */
    public static TipCatalog catalogoDeTeste(LocalDate hoje) {
        return new SimpleTipCatalog(ZoneOffset.UTC, hoje, List.of());
    }
}
