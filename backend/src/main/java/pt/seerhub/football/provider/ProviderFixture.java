package pt.seerhub.football.provider;

import java.time.Instant;

import pt.seerhub.football.domain.FixtureStatus;

/**
 * Jogo tal como o fornecedor o devolve. {@code homeScore}/{@code awayScore}
 * são sempre os golos do <b>tempo regulamentar</b> ({@code score.fulltime}) —
 * nunca os do prolongamento (§2.4-G do plano de F05).
 *
 * <p>{@code status == null} significa "estado desconhecido, não alterar o
 * estado já gravado" (caso de fronteira 8g) — nunca degradar silenciosamente
 * para {@code SCHEDULED}.
 */
public record ProviderFixture(
        long providerId, ProviderLeague league, ProviderTeam home, ProviderTeam away,
        Instant kickoffAt, FixtureStatus status, Integer homeScore, Integer awayScore) {
}
