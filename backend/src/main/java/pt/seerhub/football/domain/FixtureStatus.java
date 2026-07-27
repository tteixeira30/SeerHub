package pt.seerhub.football.domain;

/**
 * Os cinco estados de um jogo, exatamente os do {@code CHECK} de
 * {@code fixtures.status} no baseline {@code V2}.
 */
public enum FixtureStatus {
    SCHEDULED,
    LIVE,
    FINISHED,
    POSTPONED,
    CANCELLED
}
