package pt.seerhub.football.service;

import java.time.Instant;

import pt.seerhub.football.domain.FixtureStatus;

/** O catálogo do parser (F06) e do ecrã de revisão (F07) — §2.5 do plano de F05. */
public record FixtureView(
        long id, long leagueId, String leagueName, String leagueCrestUrl,
        TeamView home, TeamView away, Instant kickoffAt,
        FixtureStatus status, Integer homeScore, Integer awayScore) {
}
