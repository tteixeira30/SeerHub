package pt.seerhub.football.service;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.seerhub.football.domain.Fixture;
import pt.seerhub.football.domain.League;
import pt.seerhub.football.domain.Team;
import pt.seerhub.football.repo.FixtureRepository;
import pt.seerhub.football.repo.TeamRepository;

/**
 * A superfície de leitura pública de F05 (§2.5 do plano) — o contrato para
 * F06 (catálogo do parser), F07 (ecrã de revisão) e F08 (resolução
 * automática). Classe concreta {@code @Service}, deliberadamente sem
 * interface própria (a assinatura desta classe É o contrato).
 *
 * <p>{@code @Transactional(readOnly = true)} na classe: {@code Fixture.league}/
 * {@code homeTeam}/{@code awayTeam} são {@code FetchType.LAZY} e
 * {@code spring.jpa.open-in-view: false} está desligado (F00) — sem uma
 * sessão aberta durante a construção dos {@code *View}, aceder a
 * {@code fixture.getLeague().getName()} fora da transação lançaria
 * {@code LazyInitializationException}.
 */
@Service
@Transactional(readOnly = true)
public class FootballCatalogService {

    private final FixtureRepository fixtureRepository;
    private final TeamRepository teamRepository;
    private final CrestCacheService crestCacheService;

    public FootballCatalogService(FixtureRepository fixtureRepository, TeamRepository teamRepository,
            CrestCacheService crestCacheService) {
        this.fixtureRepository = fixtureRepository;
        this.teamRepository = teamRepository;
        this.crestCacheService = crestCacheService;
    }

    /** F08: "como acabou este jogo?" */
    public Optional<FixtureResultView> resultadoDe(long fixtureId) {
        return fixtureRepository.findById(fixtureId).map(this::paraResultado);
    }

    /** F08 em lote — uma única consulta. */
    public List<FixtureResultView> resultadosDe(Collection<Long> fixtureIds) {
        return fixtureRepository.findAllById(fixtureIds).stream().map(this::paraResultado).toList();
    }

    /** F06/F07: o catálogo de jogos sincronizados numa janela de tempo. */
    public List<FixtureView> jogosNaJanela(Instant de, Instant ate) {
        return fixtureRepository.findByKickoffAtBetweenOrderByKickoffAtAsc(de, ate).stream()
                .map(this::paraFixtureView)
                .toList();
    }

    public Optional<FixtureView> jogo(long fixtureId) {
        return fixtureRepository.findById(fixtureId).map(this::paraFixtureView);
    }

    public Optional<TeamView> equipa(long teamId) {
        return teamRepository.findById(teamId).map(this::paraTeamView);
    }

    private FixtureResultView paraResultado(Fixture fixture) {
        return new FixtureResultView(fixture.getId(), fixture.getStatus(), fixture.getHomeScore(),
                fixture.getAwayScore(), fixture.getKickoffAt(), fixture.getLastSyncedAt());
    }

    private FixtureView paraFixtureView(Fixture fixture) {
        League league = fixture.getLeague();
        return new FixtureView(
                fixture.getId(), league.getId(), league.getName(), crestCacheService.urlPublicoDaLiga(league.getId()),
                paraTeamView(fixture.getHomeTeam()), paraTeamView(fixture.getAwayTeam()),
                fixture.getKickoffAt(), fixture.getStatus(), fixture.getHomeScore(), fixture.getAwayScore());
    }

    private TeamView paraTeamView(Team team) {
        return new TeamView(team.getId(), team.getName(), team.getShortName(), team.getCountry(),
                team.getNormalizedName(), crestCacheService.urlPublicoDaEquipa(team.getId()));
    }
}
