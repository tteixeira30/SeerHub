package pt.seerhub.football.service;

import java.time.Instant;

import pt.seerhub.football.domain.FixtureStatus;

/**
 * A resposta à pergunta de F08: "como acabou este jogo?" (§2.5 do plano de
 * F05 — contrato obrigatório para F08).
 *
 * <p>{@link #homeScore}/{@link #awayScore} são sempre os golos do
 * <b>tempo regulamentar</b> (nunca os do prolongamento — ver Javadoc de
 * {@code ApiFootballDataProvider}/{@code ApiFootballStatusMapper}).
 */
public record FixtureResultView(
        long fixtureId, FixtureStatus status,
        Integer homeScore, Integer awayScore,
        Instant kickoffAt, Instant lastSyncedAt) {

    public boolean terminado() {
        return status == FixtureStatus.FINISHED && homeScore != null && awayScore != null;
    }

    public boolean naoSeRealizou() {
        return status == FixtureStatus.POSTPONED || status == FixtureStatus.CANCELLED;
    }
}
