package pt.seerhub.football.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Entidade mapeada contra a tabela {@code fixtures} do baseline {@code V2}
 * (F00). {@link #homeScore}/{@link #awayScore} são sempre os golos do
 * <b>tempo regulamentar</b> ({@code score.fulltime} da API-Football) — ver
 * o aviso completo em {@code ApiFootballDataProvider}. Gravar os golos do
 * prolongamento faria F08 resolver mal qualquer jogo de taça decidido fora
 * do tempo regulamentar.
 */
@Entity
@Table(name = "fixtures")
public class Fixture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_id", nullable = false, unique = true)
    private long providerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "league_id", nullable = false)
    private League league;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "home_team_id", nullable = false)
    private Team homeTeam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "away_team_id", nullable = false)
    private Team awayTeam;

    @Column(name = "kickoff_at", nullable = false)
    private Instant kickoffAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FixtureStatus status;

    @Column(name = "home_score")
    private Integer homeScore;

    @Column(name = "away_score")
    private Integer awayScore;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected Fixture() {
        // exigido pelo JPA
    }

    public Fixture(long providerId, League league, Team homeTeam, Team awayTeam,
            Instant kickoffAt, FixtureStatus status) {
        this.providerId = providerId;
        this.league = league;
        this.homeTeam = homeTeam;
        this.awayTeam = awayTeam;
        this.kickoffAt = kickoffAt;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public long getProviderId() {
        return providerId;
    }

    public League getLeague() {
        return league;
    }

    public Team getHomeTeam() {
        return homeTeam;
    }

    public Team getAwayTeam() {
        return awayTeam;
    }

    public Instant getKickoffAt() {
        return kickoffAt;
    }

    public void setKickoffAt(Instant kickoffAt) {
        this.kickoffAt = kickoffAt;
    }

    public FixtureStatus getStatus() {
        return status;
    }

    public void setStatus(FixtureStatus status) {
        this.status = status;
    }

    public Integer getHomeScore() {
        return homeScore;
    }

    public void setHomeScore(Integer homeScore) {
        this.homeScore = homeScore;
    }

    public Integer getAwayScore() {
        return awayScore;
    }

    public void setAwayScore(Integer awayScore) {
        this.awayScore = awayScore;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }
}
