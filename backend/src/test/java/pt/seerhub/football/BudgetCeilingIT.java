package pt.seerhub.football;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import pt.seerhub.config.SeerHubProperties;
import pt.seerhub.football.domain.FixtureStatus;
import pt.seerhub.football.provider.FixedFootballDataProvider;
import pt.seerhub.football.repo.ApiCallBudgetRepository;
import pt.seerhub.football.repo.FixtureRepository;
import pt.seerhub.football.repo.LeagueRepository;
import pt.seerhub.football.repo.PendingResultRepository;
import pt.seerhub.football.repo.TeamRepository;
import pt.seerhub.football.service.ApiCallBudgetService;
import pt.seerhub.football.service.CrestCacheService;
import pt.seerhub.football.service.FootballCatalogService;
import pt.seerhub.football.service.FootballMetrics;
import pt.seerhub.football.service.FootballSyncService;
import pt.seerhub.football.service.SeasonResolver;
import pt.seerhub.football.service.SyncOutcome;
import pt.seerhub.support.AbstractIntegrationTest;
import pt.seerhub.support.AuthTestSupport;
import pt.seerhub.support.CommunityTestSupport;
import pt.seerhub.support.FootballTestSupport;
import pt.seerhub.user.api.AuthResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * R5, critérios 3i-3m — o teto diário nunca é excedido, nunca falha
 * silenciosamente, e a reserva de catálogo protege o catálogo da fome de
 * chamadas de resultados.
 */
class BudgetCeilingIT extends AbstractIntegrationTest {

    private static final int DAILY_BUDGET = 100;
    private static final int CATALOG_RESERVE = 20;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private SeerHubProperties properties;
    @Autowired
    private FootballSyncService footballSyncService;
    @Autowired
    private FixedFootballDataProvider fake;
    @Autowired
    private Clock clock;
    @Autowired
    private ApiCallBudgetRepository budgetRepository;
    @Autowired
    private ApiCallBudgetService budgetService;
    @Autowired
    private LeagueRepository leagueRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private FixtureRepository fixtureRepository;
    @Autowired
    private PendingResultRepository pendingResultRepository;
    @Autowired
    private CrestCacheService crestCacheService;
    @Autowired
    private FootballMetrics metrics;
    @Autowired
    private FootballCatalogService footballCatalogService;

    private FootballTestSupport footballTestSupport;
    private CommunityTestSupport communityTestSupport;
    private AuthTestSupport authTestSupport;

    private long tipId;
    private long tipCriada;
    private final AtomicLong contadorId = new AtomicLong(88_000_000L + (System.nanoTime() % 90_000L));
    private final List<Long> jogosCriados = new ArrayList<>();
    private final List<Long> equipasCriadas = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        footballTestSupport = new FootballTestSupport(jdbcTemplate);
        communityTestSupport = new CommunityTestSupport(mockMvc, objectMapper, jdbcTemplate);
        authTestSupport = new AuthTestSupport(mockMvc, objectMapper, jdbcTemplate, properties);

        footballTestSupport.limparOrcamento();
        fake.limparRegisto();

        AuthResponse auth = authTestSupport.registarEAutenticar(
                authTestSupport.emailUnico("football-budget"), "password12345");
        var community = communityTestSupport.criar(auth.accessToken(), communityTestSupport.nomeUnico("liga-budget"));
        tipId = footballTestSupport.inserirTip(community.id(), auth.user().id(), "PENDING");
        tipCriada = tipId;
    }

    @AfterEach
    void tearDown() {
        footballTestSupport.apagarTip(tipCriada);
        for (Long fixtureId : jogosCriados) {
            footballTestSupport.apagarJogo(fixtureId);
        }
        for (Long teamId : equipasCriadas) {
            footballTestSupport.apagarEquipa(teamId);
        }
    }

    @Test
    void aoAtingirOTetoDiarioASincronizacaoDeResultadosDeixaDeChamar() throws Exception {
        // Pré-condição: um emblema já em cache antes de esgotar o orçamento,
        // para provar que a leitura continua a responder 200 depois.
        footballSyncService.sincronizarCatalogo();
        Map<String, Object> equipaComCrest = footballTestSupport.lerEquipaPorProviderId(940101L);
        long teamId = ((Number) equipaComCrest.get("id")).longValue();

        long liga = footballTestSupport.inserirLiga(88001L, "Liga de Teste", 2026);
        long jogo = criarJogo(liga, 88001L, Duration.ofHours(3));
        footballTestSupport.inserirSelecaoPendente(tipId, jogo);

        fake.limparRegisto();
        footballTestSupport.esgotarOrcamentoDoDia(DAILY_BUDGET);

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(fake.chamadas()).isEmpty();
        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(resultado.gruposConsiderados()).isEqualTo(1);
        assertThat(resultado.gruposIgnoradosPorOrcamento()).isEqualTo(1);

        Map<String, Object> jogoDepois = footballTestSupport.lerJogo(jogo);
        assertThat(jogoDepois.get("status")).isEqualTo("SCHEDULED");

        mockMvc.perform(get("/api/football/crests/teams/{id}", teamId)).andExpect(status().isOk());
        assertThat(footballCatalogService.equipa(teamId)).isPresent();
    }

    @Test
    void oResultadoDaSincronizacaoReportaOsGruposIgnoradosPorOrcamento() {
        long ligaA = footballTestSupport.inserirLiga(88002L, "Liga A de Teste", 2026);
        long ligaB = footballTestSupport.inserirLiga(88003L, "Liga B de Teste", 2026);
        long jogoA = criarJogo(ligaA, 88002L, Duration.ofHours(3));
        long jogoB = criarJogo(ligaB, 88003L, Duration.ofHours(4));
        footballTestSupport.inserirSelecaoPendente(tipId, jogoA);
        footballTestSupport.inserirSelecaoPendente(tipId, jogoB);

        footballTestSupport.esgotarOrcamentoDoDia(DAILY_BUDGET);

        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.gruposConsiderados()).isEqualTo(2);
        assertThat(resultado.gruposIgnoradosPorOrcamento()).isEqualTo(2);
        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
    }

    @Test
    void aReservaDeCatalogoImpedeQueOsResultadosEsgotemAQuotaDoCatalogo() {
        long liga = footballTestSupport.inserirLiga(88004L, "Liga de Teste", 2026);
        long jogo = criarJogo(liga, 88004L, Duration.ofHours(3));
        footballTestSupport.inserirSelecaoPendente(tipId, jogo);
        fake.definirResultado(88004L, 2026, dataUtc(jogo), providerIdDoJogo(jogo), FixtureStatus.FINISHED, 1, 0);

        footballTestSupport.esgotarOrcamentoDoDia(DAILY_BUDGET - CATALOG_RESERVE);

        SyncOutcome resultadoDeResultados = footballSyncService.sincronizarResultadosPendentes();
        assertThat(resultadoDeResultados.chamadasEfetuadas()).isEqualTo(0);
        assertThat(resultadoDeResultados.gruposIgnoradosPorOrcamento()).isEqualTo(1);

        fake.limparRegisto();
        SyncOutcome resultadoDeCatalogo = footballSyncService.sincronizarCatalogo();
        // 6 chamadas de catálogo (uma por liga) + no máximo 1 de backfill — mesma
        // aritmética flexível do critério 1f, para não depender do estado de
        // backfill já convergido (ou não) por outras classes na mesma suite.
        assertThat(resultadoDeCatalogo.chamadasEfetuadas()).isBetween(6, 7);
    }

    @Test
    void oLimiteDeQuotaDoFornecedorEsgotaODiaEInterrompeOCiclo() {
        long liga = footballTestSupport.inserirLiga(88005L, "Liga de Teste", 2026);
        long jogo = criarJogo(liga, 88005L, Duration.ofHours(3));
        footballTestSupport.inserirSelecaoPendente(tipId, jogo);

        fake.devolverQuotaEsgotada();
        SyncOutcome resultado = footballSyncService.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(0);
        assertThat(resultado.falhas()).isEqualTo(1);
        assertThat(footballTestSupport.consumoDoDia()).isEqualTo(DAILY_BUDGET);

        // O ciclo interrompeu-se: uma segunda reserva no mesmo dia já não é concedida.
        assertThat(budgetService.tentarReservar(DAILY_BUDGET)).isFalse();
    }

    @Test
    void noDiaSeguinteASincronizacaoVoltaAChamar() {
        long liga = footballTestSupport.inserirLiga(88006L, "Liga de Teste", 2026);
        long jogo = criarJogo(liga, 88006L, Duration.ofHours(3));
        footballTestSupport.inserirSelecaoPendente(tipId, jogo);
        fake.definirResultado(88006L, 2026, dataUtc(jogo), providerIdDoJogo(jogo), FixtureStatus.FINISHED, 2, 0);

        footballTestSupport.esgotarOrcamentoDoDia(DAILY_BUDGET);

        Clock amanha = Clock.offset(clock, Duration.ofDays(1));
        ApiCallBudgetService budgetServiceDeAmanha = new ApiCallBudgetService(budgetRepository, amanha);
        SeasonResolver seasonResolverDeAmanha = new SeasonResolver(amanha, properties);
        FootballSyncService servicoDeAmanha = new FootballSyncService(fake, leagueRepository, teamRepository,
                fixtureRepository, pendingResultRepository, budgetServiceDeAmanha, seasonResolverDeAmanha,
                crestCacheService, properties, amanha, metrics);

        SyncOutcome resultado = servicoDeAmanha.sincronizarResultadosPendentes();

        assertThat(resultado.chamadasEfetuadas()).isEqualTo(1);
        assertThat(footballTestSupport.consumoDoDia(LocalDate.now(clock.withZone(ZoneOffset.UTC)))).isEqualTo(DAILY_BUDGET);
    }

    private long criarJogo(long ligaId, long ligaProviderId, Duration desdeOInicio) {
        Instant kickoffAt = clock.instant().minus(desdeOInicio);
        long homeId = criarEquipa();
        long awayId = criarEquipa();
        long fixtureProviderId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirJogo(fixtureProviderId, ligaId, homeId, awayId, kickoffAt, "SCHEDULED");
        jogosCriados.add(id);
        return id;
    }

    private long criarEquipa() {
        long providerId = contadorId.incrementAndGet();
        long id = footballTestSupport.inserirEquipa(providerId, "Equipa " + providerId, "equipa " + providerId);
        equipasCriadas.add(id);
        return id;
    }

    private long providerIdDoJogo(long fixtureId) {
        return (Long) footballTestSupport.lerColunaDoJogo(fixtureId, "provider_id");
    }

    private LocalDate dataUtc(long fixtureId) {
        java.sql.Timestamp kickoff = (java.sql.Timestamp) footballTestSupport.lerColunaDoJogo(fixtureId, "kickoff_at");
        return kickoff.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
    }
}
