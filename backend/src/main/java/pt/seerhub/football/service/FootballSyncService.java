package pt.seerhub.football.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.Fixture;
import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.domain.League;
import pt.seerhub.football.domain.Team;
import pt.seerhub.football.provider.FootballDataProvider;
import pt.seerhub.football.provider.FootballProviderException;
import pt.seerhub.football.provider.ProviderFixture;
import pt.seerhub.football.provider.ProviderLeague;
import pt.seerhub.football.provider.ProviderTeam;
import pt.seerhub.football.repo.FixtureRepository;
import pt.seerhub.football.repo.LeagueRepository;
import pt.seerhub.football.repo.PendingResultGroup;
import pt.seerhub.football.repo.PendingResultRepository;
import pt.seerhub.football.repo.TeamRepository;

/**
 * O motor de sincronização de F05 (R5). Duas operações independentes:
 *
 * <ul>
 *   <li>{@link #sincronizarCatalogo()} — agendada de 12 em 12 horas,
 *   mantém ligas/equipas/jogos dos próximos 7 dias frescos.</li>
 *   <li>{@link #sincronizarResultadosPendentes()} — agendada de 15 em 15
 *   minutos, mas só chama o fornecedor quando existe procura real (uma
 *   seleção pendente cujo jogo já começou há mais de 2h e ainda não
 *   terminou), agrupando por liga e dia (§2.4 do plano de F05).</li>
 * </ul>
 *
 * <p><b>É a única classe que depende de {@link FootballDataProvider}</b>
 * (verificado por {@code FootballConventionsTest.soOServicoDeSincronizacaoDependeDoFornecedor}).
 * <b>Nunca escreve em {@code tip_selections} nem em {@code tips}</b> — lê-as
 * só por {@link PendingResultRepository} (SQL nativo).
 *
 * <p>Guarda de sobreposição com {@link ReentrantLock#tryLock()}: uma segunda
 * execução concorrente da mesma operação devolve {@link SyncOutcome#ignorado()}
 * de imediato, sem tocar no orçamento (caso de fronteira X4).
 */
@Service
public class FootballSyncService {

    private static final Logger log = LoggerFactory.getLogger(FootballSyncService.class);

    private final FootballDataProvider provider;
    private final LeagueRepository leagueRepository;
    private final TeamRepository teamRepository;
    private final FixtureRepository fixtureRepository;
    private final PendingResultRepository pendingResultRepository;
    private final ApiCallBudgetService budgetService;
    private final SeasonResolver seasonResolver;
    private final CrestCacheService crestCacheService;
    private final SeerHubProperties properties;
    private final Clock clock;
    private final FootballMetrics metrics;

    private final ReentrantLock catalogLock = new ReentrantLock();
    private final ReentrantLock resultLock = new ReentrantLock();

    public FootballSyncService(FootballDataProvider provider, LeagueRepository leagueRepository,
            TeamRepository teamRepository, FixtureRepository fixtureRepository,
            PendingResultRepository pendingResultRepository, ApiCallBudgetService budgetService,
            SeasonResolver seasonResolver, CrestCacheService crestCacheService,
            SeerHubProperties properties, Clock clock, FootballMetrics metrics) {
        this.provider = provider;
        this.leagueRepository = leagueRepository;
        this.teamRepository = teamRepository;
        this.fixtureRepository = fixtureRepository;
        this.pendingResultRepository = pendingResultRepository;
        this.budgetService = budgetService;
        this.seasonResolver = seasonResolver;
        this.crestCacheService = crestCacheService;
        this.properties = properties;
        this.clock = clock;
        this.metrics = metrics;
    }

    // === Catálogo (R5, critério 1) ===

    public SyncOutcome sincronizarCatalogo() {
        if (!catalogLock.tryLock()) {
            return SyncOutcome.ignorado();
        }
        try {
            return executarSincronizacaoDoCatalogo();
        } finally {
            catalogLock.unlock();
        }
    }

    private SyncOutcome executarSincronizacaoDoCatalogo() {
        SeerHubProperties.Football football = propriedadesDeFootball();
        List<SeerHubProperties.Football.LeagueConfig> ligas = football.ligasOuOmissao();
        int epoca = seasonResolver.epocaAtual();
        LocalDate hoje = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        LocalDate ate = hoje.plusDays(football.horizonteDoCatalogo().toDays());
        int limiteDeCatalogo = football.orcamentoDiario();

        crestCacheService.reiniciarContadorDeExecucao();

        int chamadas = 0;
        int falhas = 0;
        int jogosAtualizados = 0;
        boolean quotaEsgotada = false;

        for (SeerHubProperties.Football.LeagueConfig ligaConfig : ligas) {
            if (quotaEsgotada || ligaConfig.providerId() == null) {
                continue;
            }
            if (!budgetService.tentarReservar(limiteDeCatalogo)) {
                metrics.registarSemOrcamento();
                log.warn("Orçamento diário esgotado; sincronização do catálogo interrompida antes da liga {}.",
                        ligaConfig.providerId());
                break;
            }
            try {
                List<ProviderFixture> fixtures = provider.jogosDaLigaEntre(ligaConfig.providerId(), epoca, hoje, ate);
                chamadas++;
                metrics.registarSucesso();
                for (ProviderFixture pf : fixtures) {
                    upsertFixture(pf);
                    jogosAtualizados++;
                }
                log.info("Sincronização do catálogo: liga {} devolveu {} jogo(s).",
                        ligaConfig.providerId(), fixtures.size());
            } catch (FootballProviderException ex) {
                falhas++;
                metrics.registarFalha();
                if (ex.quotaExcedida()) {
                    budgetService.esgotarDiaPorSinalDoFornecedor(football.orcamentoDiario());
                    quotaEsgotada = true;
                    log.warn("Fornecedor sinalizou quota esgotada durante a sincronização do catálogo.", ex);
                } else {
                    log.warn("Falha ao sincronizar a liga {} (as restantes continuam): {}",
                            ligaConfig.providerId(), ex.getMessage());
                }
            }
        }

        if (!quotaEsgotada) {
            executarBackfillDeNomeCurto(epoca, limiteDeCatalogo);
        }

        return new SyncOutcome(ligas.size(), chamadas, 0, jogosAtualizados, falhas, false);
    }

    /** Backfill limitado: no máximo uma liga por execução (5c do plano). */
    private void executarBackfillDeNomeCurto(int epoca, int limiteDeCatalogo) {
        List<Long> ligaIds = teamRepository.idsDeLigasComEquipasSemNomeCurto();
        if (ligaIds.isEmpty()) {
            return;
        }
        Optional<League> ligaOpt = leagueRepository.findById(ligaIds.get(0));
        if (ligaOpt.isEmpty()) {
            return;
        }
        League liga = ligaOpt.get();
        if (!budgetService.tentarReservar(limiteDeCatalogo)) {
            return;
        }
        try {
            List<ProviderTeam> equipas = provider.equipasDaLiga(liga.getProviderId(), epoca);
            for (ProviderTeam pt : equipas) {
                teamRepository.findByProviderId(pt.providerId()).ifPresent(team -> {
                    boolean alterado = false;
                    if (pt.shortName() != null) {
                        team.setShortName(pt.shortName());
                        alterado = true;
                    }
                    if (pt.country() != null) {
                        team.setCountry(pt.country());
                        alterado = true;
                    }
                    if (alterado) {
                        teamRepository.save(team);
                    }
                });
            }
        } catch (FootballProviderException ex) {
            log.warn("Falha no backfill de nome curto da liga {}: {}", liga.getProviderId(), ex.getMessage());
        }
    }

    private void upsertFixture(ProviderFixture pf) {
        League league = upsertLeague(pf.league());
        Team home = upsertTeam(pf.home());
        Team away = upsertTeam(pf.away());

        Optional<Fixture> existenteOpt = fixtureRepository.findByProviderId(pf.providerId());
        Fixture fixture;
        if (existenteOpt.isPresent()) {
            fixture = existenteOpt.get();
            fixture.setKickoffAt(pf.kickoffAt());
            // Estado desconhecido do fornecedor nunca degrada o estado já gravado (8g).
            if (pf.status() != null) {
                fixture.setStatus(pf.status());
            }
            if (pf.homeScore() != null) {
                fixture.setHomeScore(pf.homeScore());
            }
            if (pf.awayScore() != null) {
                fixture.setAwayScore(pf.awayScore());
            }
        } else {
            FixtureStatus statusInicial = pf.status() != null ? pf.status() : FixtureStatus.SCHEDULED;
            fixture = new Fixture(pf.providerId(), league, home, away, pf.kickoffAt(), statusInicial);
            fixture.setHomeScore(pf.homeScore());
            fixture.setAwayScore(pf.awayScore());
        }
        fixtureRepository.save(fixture);

        garantirEmblemaDaLiga(league);
        garantirEmblemaDaEquipa(home);
        garantirEmblemaDaEquipa(away);
    }

    /**
     * É esta classe — a única que depende de {@link FootballDataProvider} —
     * quem decide chamar {@code provider.emblema(url)} e quem entrega os
     * bytes já obtidos a {@link CrestCacheService}; a cache em si nunca
     * depende do fornecedor (7b do plano).
     */
    private void garantirEmblemaDaEquipa(Team team) {
        String logoUrl = team.getLogoUrl();
        if (!crestCacheService.precisaDeDescarregarEquipa(team.getId(), logoUrl)) {
            return;
        }
        provider.emblema(logoUrl).ifPresent(bytes -> crestCacheService.gravarEmblemaDaEquipa(team.getId(), logoUrl, bytes));
    }

    private void garantirEmblemaDaLiga(League league) {
        String logoUrl = league.getLogoUrl();
        if (!crestCacheService.precisaDeDescarregarLiga(league.getId(), logoUrl)) {
            return;
        }
        provider.emblema(logoUrl).ifPresent(bytes -> crestCacheService.gravarEmblemaDaLiga(league.getId(), logoUrl, bytes));
    }

    private League upsertLeague(ProviderLeague pl) {
        Optional<League> existenteOpt = leagueRepository.findByProviderId(pl.providerId());
        League league;
        if (existenteOpt.isPresent()) {
            league = existenteOpt.get();
            league.setName(pl.name());
            league.setCountry(pl.country());
            league.setLogoUrl(pl.logoUrl());
            league.setSeason(pl.season());
        } else {
            league = new League(pl.providerId(), pl.name(), pl.country(), pl.logoUrl(), pl.season());
        }
        return leagueRepository.save(league);
    }

    private Team upsertTeam(ProviderTeam pt) {
        Optional<Team> existenteOpt = teamRepository.findByProviderId(pt.providerId());
        Team team;
        // O nome normalizado é sempre gerado aqui, nunca pelo chamador (6e do plano).
        String nomeNormalizado = pt.name() != null ? pt.name() : "";
        if (existenteOpt.isPresent()) {
            team = existenteOpt.get();
            team.setName(pt.name());
            team.setNormalizedName(pt.name() != null
                    ? TeamNameNormalizer.normalizar(pt.name())
                    : team.getNormalizedName());
            if (pt.shortName() != null) {
                team.setShortName(pt.shortName());
            }
            if (pt.country() != null) {
                team.setCountry(pt.country());
            }
            if (pt.logoUrl() != null) {
                team.setLogoUrl(pt.logoUrl());
            }
        } else {
            team = new Team(pt.providerId(), pt.name(), TeamNameNormalizer.normalizar(nomeNormalizado));
            team.setShortName(pt.shortName());
            team.setCountry(pt.country());
            team.setLogoUrl(pt.logoUrl());
        }
        return teamRepository.save(team);
    }

    // === Resultados a pedido (R5, critério 2) ===

    public SyncOutcome sincronizarResultadosPendentes() {
        if (!resultLock.tryLock()) {
            return SyncOutcome.ignorado();
        }
        try {
            return executarSincronizacaoDeResultados();
        } finally {
            resultLock.unlock();
        }
    }

    private SyncOutcome executarSincronizacaoDeResultados() {
        SeerHubProperties.Football football = propriedadesDeFootball();
        Instant agora = clock.instant();
        Instant limiteDeArranque = agora.minus(football.atrasoDeResultado());
        Instant limiteInferior = agora.minus(football.janelaDeResultados());
        Instant limiteDeRepeticao = agora.minus(football.intervaloMinimoEntreResultados());
        int maximo = football.maximoDeGruposPorExecucao();

        List<PendingResultGroup> grupos = pendingResultRepository.gruposPorResolver(
                limiteDeArranque, limiteInferior, limiteDeRepeticao, maximo);
        if (grupos.isEmpty()) {
            return SyncOutcome.semTrabalho();
        }

        int limiteDeResultados = football.orcamentoDiario() - football.reservaDeCatalogo();
        int chamadas = 0;
        int ignoradosPorOrcamento = 0;
        int jogosAtualizados = 0;
        int falhas = 0;
        boolean quotaEsgotada = false;

        for (PendingResultGroup grupo : grupos) {
            if (quotaEsgotada) {
                ignoradosPorOrcamento++;
                continue;
            }
            if (!budgetService.tentarReservar(limiteDeResultados)) {
                ignoradosPorOrcamento++;
                metrics.registarSemOrcamento();
                log.warn("Orçamento de resultados esgotado; grupo liga={} dia={} ignorado.",
                        grupo.ligaProviderId(), grupo.dia());
                continue;
            }
            try {
                List<ProviderFixture> resultado = provider.jogosDaLigaNoDia(
                        grupo.ligaProviderId(), grupo.epoca(), grupo.dia());
                chamadas++;
                metrics.registarSucesso();
                for (ProviderFixture pf : resultado) {
                    fixtureRepository.findByProviderId(pf.providerId()).ifPresent(fixture -> aplicarResultado(fixture, pf));
                }

                Instant inicioDoDia = grupo.dia().atStartOfDay(ZoneOffset.UTC).toInstant();
                Instant fimDoDia = inicioDoDia.plus(Duration.ofDays(1));
                jogosAtualizados += fixtureRepository.marcarSincronizados(grupo.ligaId(), inicioDoDia, fimDoDia, agora);
            } catch (FootballProviderException ex) {
                falhas++;
                metrics.registarFalha();
                if (ex.quotaExcedida()) {
                    budgetService.esgotarDiaPorSinalDoFornecedor(football.orcamentoDiario());
                    quotaEsgotada = true;
                    log.warn("Fornecedor sinalizou quota esgotada durante a sincronização de resultados; ciclo interrompido.", ex);
                } else {
                    log.warn("Falha ao sincronizar resultados da liga {} dia {}: {}",
                            grupo.ligaProviderId(), grupo.dia(), ex.getMessage());
                }
            }
        }

        return new SyncOutcome(grupos.size(), chamadas, ignoradosPorOrcamento, jogosAtualizados, falhas, false);
    }

    private void aplicarResultado(Fixture fixture, ProviderFixture pf) {
        if (pf.status() != null) {
            fixture.setStatus(pf.status());
        }
        if (pf.homeScore() != null) {
            fixture.setHomeScore(pf.homeScore());
        }
        if (pf.awayScore() != null) {
            fixture.setAwayScore(pf.awayScore());
        }
        fixtureRepository.save(fixture);
    }

    private SeerHubProperties.Football propriedadesDeFootball() {
        SeerHubProperties.Football football = properties.football();
        return football != null ? football
                : new SeerHubProperties.Football(null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
