package pt.seerhub.football.provider;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;

import pt.seerhub.football.domain.FixtureStatus;

/**
 * Isola a peça mais frágil do fornecedor real: o mapeamento dos códigos
 * curtos de estado da API-Football para os cinco estados do baseline, e a
 * extração dos golos do <b>tempo regulamentar</b> (R5, casos de fronteira
 * 8g/8h/8i).
 *
 * <p><b>Golos gravados (§2.4-G do plano de F05, aviso em maiúsculas para
 * quem vier a seguir):</b> usar sempre {@code score.fulltime}, NUNCA
 * {@code goals} diretamente — {@code goals} inclui o prolongamento, e
 * gravar esse valor faria F08 resolver mal qualquer jogo de taça decidido
 * fora do tempo regulamentar (1X2, dupla hipótese, mais/menos golos e ambas
 * marcam resolvem-se sempre ao tempo regulamentar). {@code goals} só é
 * usado como recurso quando {@code score.fulltime} não vem preenchido.
 */
public final class ApiFootballStatusMapper {

    private static final Map<String, FixtureStatus> MAPA = Map.ofEntries(
            Map.entry("NS", FixtureStatus.SCHEDULED),
            Map.entry("1H", FixtureStatus.LIVE),
            Map.entry("HT", FixtureStatus.LIVE),
            Map.entry("2H", FixtureStatus.LIVE),
            Map.entry("ET", FixtureStatus.LIVE),
            Map.entry("BT", FixtureStatus.LIVE),
            Map.entry("P", FixtureStatus.LIVE),
            Map.entry("SUSP", FixtureStatus.LIVE),
            Map.entry("INT", FixtureStatus.LIVE),
            Map.entry("LIVE", FixtureStatus.LIVE),
            Map.entry("FT", FixtureStatus.FINISHED),
            Map.entry("AET", FixtureStatus.FINISHED),
            Map.entry("PEN", FixtureStatus.FINISHED),
            Map.entry("AWD", FixtureStatus.FINISHED),
            Map.entry("WO", FixtureStatus.FINISHED),
            Map.entry("PST", FixtureStatus.POSTPONED),
            Map.entry("CANC", FixtureStatus.CANCELLED),
            Map.entry("ABD", FixtureStatus.CANCELLED));

    private ApiFootballStatusMapper() {
    }

    /**
     * {@code Optional.empty()} para um código desconhecido (incluindo
     * {@code TBD}, "hora por confirmar") — o chamador (o {@code upsert} de
     * {@code FootballSyncService}) tem de preservar o estado já gravado
     * nesse caso, nunca degradar para {@code SCHEDULED}.
     */
    public static Optional<FixtureStatus> mapear(String codigoCurto) {
        if (codigoCurto == null) {
            return Optional.empty();
        }
        FixtureStatus status = MAPA.get(codigoCurto.toUpperCase(Locale.ROOT));
        return Optional.ofNullable(status);
    }

    /**
     * Golos do tempo regulamentar: {@code score.fulltime.{home,away}}, com
     * recurso a {@code goals.{home,away}} só quando {@code fulltime} não
     * vier preenchido. NUNCA usar {@code goals} quando {@code fulltime}
     * existir — ver Javadoc da classe.
     */
    public static Integer[] golosRegulamentares(JsonNode fixtureNode) {
        JsonNode fulltime = fixtureNode.path("score").path("fulltime");
        Integer home = numeroOuNulo(fulltime.path("home"));
        Integer away = numeroOuNulo(fulltime.path("away"));

        if (home == null && away == null) {
            JsonNode goals = fixtureNode.path("goals");
            home = numeroOuNulo(goals.path("home"));
            away = numeroOuNulo(goals.path("away"));
        }
        return new Integer[]{home, away};
    }

    private static Integer numeroOuNulo(JsonNode node) {
        return node.isInt() ? node.asInt() : null;
    }
}
